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
      monthlySalary: 11300000,
      currency: "COP",
      displayName: "Alice",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
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
      salary: 11300000,
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
      monthlySalary: 11300000,
      currency: "COP",
      ghostField: "malicious",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
