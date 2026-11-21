const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_123";
const BOB_UID = "bob_user_456";

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

test("Unauthenticated user cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("history").get()
  );
});

test("Owner can create, update, and delete UserProfile; rejects ghost fields and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const nowPast = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      subscriptionTier: "FREE",
      analyticsEnabled: true,
      personalizationEnabled: true,
      preferredQuality: "HIGH",
      createdAt: nowPast,
      updatedAt: nowPast,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredQuality: "LOSSLESS",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt tamper
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice2",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user isolation
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
});

test("Owner can manage CloudPlaylists; rejects @DocumentId 'id' key and cross-user reads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const nowPast = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_workout_1")
      .set({
        userId: ALICE_UID,
        name: "Workout Energy",
        description: "High BPM tracks",
        trackIds: ["bundled_neon_horizon", "bundled_midnight_raag"],
        smartPlaylist: false,
        createdAt: nowPast,
        updatedAt: nowPast,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_workout_1")
      .update({
        name: "Workout Pulse",
        trackIds: ["bundled_neon_horizon"],
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject extra 'id' field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_bad")
      .set({
        id: "pl_bad",
        userId: ALICE_UID,
        name: "Bad",
        trackIds: [],
        smartPlaylist: false,
        createdAt: nowPast,
        updatedAt: nowPast,
      })
  );

  // Cross-user isolation
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_workout_1")
      .delete()
  );
});

test("Owner can manage FavoriteTracks and ListeningHistory", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const nowPast = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("bundled_neon_horizon")
      .set({
        userId: ALICE_UID,
        trackId: "bundled_neon_horizon",
        title: "Neon Horizon",
        artist: "Aarav Synth",
        album: "Liquid Cosmos",
        durationMs: 36000,
        sourceType: "BUNDLED",
        createdAt: nowPast,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("history")
      .doc("bundled_neon_horizon")
      .set({
        userId: ALICE_UID,
        trackId: "bundled_neon_horizon",
        title: "Neon Horizon",
        artist: "Aarav Synth",
        album: "Liquid Cosmos",
        genre: "Electronic",
        playCount: 1,
        lastPlayedAt: nowPast,
        createdAt: nowPast,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("history")
      .doc("bundled_neon_horizon")
      .update({
        playCount: 2,
        lastPlayedAt: new Date(Date.now() - 1000),
      })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("history").get()
  );
});
