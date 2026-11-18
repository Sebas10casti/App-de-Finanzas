const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create and read their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      monthlySalary: 5000000,
      currency: "COP",
      displayName: "Alice",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      hiddenTabs: ["METRICS", "AI_ADVISOR"],
      showSavingsTab: true,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      hiddenTabs: "METRICS",
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: cannot read another user's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      monthlySalary: 5000000,
      currency: "COP",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Authenticated user: can manage items in their own monthly budget", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const monthRef = aliceDb.collection("users").doc(ALICE_UID).collection("months").doc("2026-11");
  await assertSucceeds(
    monthRef.set({
      userId: ALICE_UID,
      monthKey: "2026-11",
      salary: 5000000,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const itemRef = monthRef.collection("items").doc("item_arriendo");
  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      monthKey: "2026-11",
      name: "Arriendo",
      amount: 1400000,
      category: "Gastos Hogar",
      isPaid: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob cannot read Alice's items
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("months").doc("2026-11").collection("items").doc("item_arriendo").get());
});

test("Ghost field attack: rejected by strict keys", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      monthlySalary: 5000000,
      currency: "COP",
      ghostField: "malicious",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Savings pockets: owner creates, shares via temporary code, partner joins, edits, and leaves", async () => {
  const CHARLIE_UID = "charlie_789";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date();
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000);

  const pocketRef = aliceDb.collection("savingsPockets").doc("pocket_1");
  await assertSucceeds(
    pocketRef.set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      name: "Viaje a Europa",
      currentAmount: 500000,
      targetAmount: 5000000,
      iconKey: "flight",
      colorKey: "blue",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-member Bob and unauthenticated users cannot read or query Alice's pocket
  await assertFails(bobDb.collection("savingsPockets").doc("pocket_1").get());
  await assertFails(bobDb.collection("savingsPockets").get());
  await assertFails(testEnv.unauthenticatedContext().firestore().collection("savingsPockets").doc("pocket_1").get());

  // Alice activates temporary share code
  await assertSucceeds(
    pocketRef.update({
      shareCode: "AHO84K2",
      shareCodeExpiresAt: tomorrow,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("pocketInvites").doc("AHO84K2").set({
      pocketId: "pocket_1",
      pocketName: "Viaje a Europa",
      ownerId: ALICE_UID,
      ownerName: "Alice",
      isActive: true,
      expiresAt: tomorrow,
      createdAt: now,
    })
  );

  // Listing all invites is forbidden (only exact lookup by code is allowed)
  await assertFails(bobDb.collection("pocketInvites").get());
  await assertSucceeds(bobDb.collection("pocketInvites").doc("AHO84K2").get());

  // Bob joins using active code without tampering with owner or balance
  await assertSucceeds(
    bobDb.collection("savingsPockets").doc("pocket_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      [`memberNames.${BOB_UID}`]: "Bob",
      updatedAt: new Date(),
    })
  );

  // Charlie cannot hijack pocket balance during join
  await assertFails(
    charlieDb.collection("savingsPockets").doc("pocket_1").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      currentAmount: 0,
      updatedAt: new Date(),
    })
  );

  // Bob can now query his pockets, update goal/balance, and record a movement
  await assertSucceeds(bobDb.collection("savingsPockets").where("memberUids", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("savingsPockets").doc("pocket_1").update({
      currentAmount: 700000,
      targetAmount: 6000000,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("savingsPockets").doc("pocket_1").collection("movements").doc("mov_1").set({
      pocketId: "pocket_1",
      userId: BOB_UID,
      userName: "Bob",
      type: "deposit",
      amount: 200000,
      balanceAfter: 700000,
      note: "Aporte quincena",
      createdAt: now,
    })
  );

  // Bob cannot delete the pocket or change shareCode, and movements are immutable
  await assertFails(bobDb.collection("savingsPockets").doc("pocket_1").delete());
  await assertFails(
    bobDb.collection("savingsPockets").doc("pocket_1").update({
      shareCode: "HACKED",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("savingsPockets").doc("pocket_1").collection("movements").doc("mov_1").update({
      amount: 999999,
    })
  );

  // Bob can leave the pocket
  await assertSucceeds(
    bobDb.collection("savingsPockets").doc("pocket_1").update({
      memberUids: [ALICE_UID],
      updatedAt: new Date(),
    })
  );

  // After leaving, Bob immediately loses access to the pocket and its movements
  await assertFails(bobDb.collection("savingsPockets").doc("pocket_1").get());
  await assertFails(bobDb.collection("savingsPockets").doc("pocket_1").collection("movements").doc("mov_1").get());
});

test("Savings pockets: expired or deactivated invite code rejects join", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  const expired = new Date(Date.now() - 60 * 1000);

  await assertSucceeds(
    aliceDb.collection("savingsPockets").doc("pocket_exp").set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      name: "Fondo",
      currentAmount: 100000,
      iconKey: "savings",
      colorKey: "emerald",
      shareCode: "EXP1234",
      shareCodeExpiresAt: expired,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("pocketInvites").doc("EXP1234").set({
      pocketId: "pocket_exp",
      pocketName: "Fondo",
      ownerId: ALICE_UID,
      ownerName: "Alice",
      isActive: true,
      expiresAt: expired,
      createdAt: now,
    })
  );

  await assertFails(
    bobDb.collection("savingsPockets").doc("pocket_exp").update({
      memberUids: [ALICE_UID, BOB_UID],
      [`memberNames.${BOB_UID}`]: "Bob",
      updatedAt: new Date(),
    })
  );
});
