const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_lsa_123";
const BOB_UID = "bob_lsa_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated users are denied on all user subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("translations").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("frequent_phrases").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("learned_signs").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("translations")
      .doc("tr_1")
      .set({
        userId: ALICE_UID,
        languageCode: "LSA",
        rawGlosses: "HOLA",
        naturalPhrase: "¡Hola!",
        sourceMode: "GEMINI_AI",
        confidencePercent: 95,
        spokenAloud: true,
        offlineRecord: false,
        favorite: false,
        createdAt: past,
        updatedAt: past,
      })
  );
});

test("Authenticated owner can create, read, update, and list translations with Kotlin parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("translations")
    .doc("tr_101");

  await assertSucceeds(
    ref.set({
      userId: ALICE_UID,
      languageCode: "LSA",
      rawGlosses: "HOLA + COMO_ANDAS",
      naturalPhrase: "¡Hola! ¿Cómo andás?",
      sourceMode: "GEMINI_AI",
      confidencePercent: 94,
      spokenAloud: true,
      offlineRecord: false,
      favorite: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("translations").get()
  );

  await assertSucceeds(
    ref.update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    ref.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost/extra fields or @DocumentId 'id' leakage
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("translations")
      .doc("tr_102")
      .set({
        id: "tr_102",
        userId: ALICE_UID,
        languageCode: "LSA",
        rawGlosses: "GRACIAS",
        naturalPhrase: "¡Gracias!",
        sourceMode: "GEMINI_AI",
        confidencePercent: 95,
        spokenAloud: true,
        offlineRecord: false,
        favorite: false,
        createdAt: past,
        updatedAt: past,
      })
  );

  // Reject future tampered timestamps
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("translations")
      .doc("tr_103")
      .set({
        userId: ALICE_UID,
        languageCode: "LSA",
        rawGlosses: "GRACIAS",
        naturalPhrase: "¡Gracias!",
        sourceMode: "GEMINI_AI",
        confidencePercent: 95,
        spokenAloud: true,
        offlineRecord: false,
        favorite: false,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );
});

test("Authenticated owner can manage frequent_phrases and learned_signs; cross-user access denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const phraseRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("frequent_phrases")
    .doc("phrase_1");

  await assertSucceeds(
    phraseRef.set({
      userId: ALICE_UID,
      title: "Presentación",
      phraseText: "Hola, uso LSA.",
      category: "Comunicación",
      languageCode: "LSA",
      usageCount: 1,
      pinned: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    phraseRef.update({
      usageCount: 2,
      pinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const signRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("learned_signs")
    .doc("sign_hola");

  await assertSucceeds(
    signRef.set({
      userId: ALICE_UID,
      glossKey: "HOLA",
      displayTitle: "Hola",
      idiomaticOutput: "¡Hola!",
      category: "Saludos",
      languageCode: "LSA",
      handshapeGuide: "Palma abierta en la sien.",
      facialCue: "Sonrisa amable.",
      usageCount: 1,
      masteryPercent: 40,
      pinned: true,
      userCreated: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    signRef.update({
      usageCount: 2,
      masteryPercent: 48,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user isolation checks
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("frequent_phrases").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("frequent_phrases")
      .doc("phrase_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("learned_signs")
      .doc("sign_hola")
      .update({ masteryPercent: 100, updatedAt: new Date(Date.now() - 1000) })
  );
});
