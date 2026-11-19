const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_engineer_123";
const BOB_UID = "bob_manager_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDocumentPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Material Requisition Slip #6717 — Nut Bolt 8mm",
    category: "MATERIAL_SLIP",
    documentDateIso: "2026-02-24",
    filedDateIso: "2026-11-19",
    referenceNumber: "6717 / FCN-ST-13 (R1)",
    organization: "Kalpataru Projects International Ltd.",
    contractorOrParty: "Apex Solar — MEP",
    summary: "Issued 440 units of Nut Bolt 8mm (Returnable) to Apex Solar MEP dept.",
    extractedFields: [
      "Item 1: Nut Bolt 8mm (Required: 440, Issued: 440)",
      "Department: MEP",
      "Issue Type: Free/Returnable",
    ],
    ocrFullText: "KALPATARU PROJECTS INTERNATIONAL LTD. Material Requistion & Issue Slip 6717 Date: 24/02/2026 Apex Solar MEP Nut Bolt 8mm 440 440",
    localImageFileName: "doc_1_scan.jpg",
    localPdfFileName: "doc_1_searchable.pdf",
    previewBase64: "dGVzdF9wcmV2aWV3",
    pageCount: 1,
    starred: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user: cannot list, get, or create documents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("doc_1")
      .set(validDocumentPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid Kotlin create, list, edit, star toggle, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docsCol = aliceDb.collection("users").doc(ALICE_UID).collection("documents");

  // Empty subcollection list must succeed
  await assertSucceeds(docsCol.get());

  // Create with valid Kotlin-aligned payload (no 'id' field)
  await assertSucceeds(docsCol.doc("doc_slip_6717").set(validDocumentPayload(ALICE_UID)));

  // Create blank-date document (e.g. blank Solar Inspection Protocol)
  await assertSucceeds(
    docsCol.doc("doc_solar_proto").set(
      validDocumentPayload(ALICE_UID, {
        title: "Solar System Inspection Protocol",
        category: "INSPECTION_PROTOCOL",
        documentDateIso: "",
        extractedFields: [],
      })
    )
  );

  // List and get succeed for owner
  await assertSucceeds(docsCol.get());
  await assertSucceeds(docsCol.doc("doc_slip_6717").get());

  // Partial update: Edit metadata action
  await assertSucceeds(
    docsCol.doc("doc_slip_6717").update({
      title: "Material Slip #6717 (Verified)",
      documentDateIso: "2026-01-12",
      filedDateIso: "2026-01-12",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update: Toggle star action
  await assertSucceeds(
    docsCol.doc("doc_slip_6717").update({
      starred: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(docsCol.doc("doc_solar_proto").delete());
});

test("Security invariants: rejects shadow fields, id field, immutable edits, future timestamps, invalid dates/categories", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docsCol = aliceDb.collection("users").doc(ALICE_UID).collection("documents");

  // Reject '@DocumentId' id field written into document body
  await assertFails(
    docsCol.doc("doc_bad_id").set(validDocumentPayload(ALICE_UID, { id: "doc_bad_id" }))
  );

  // Reject shadow/ghost field
  await assertFails(
    docsCol.doc("doc_ghost").set(validDocumentPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject invalid category
  await assertFails(
    docsCol.doc("doc_bad_cat").set(validDocumentPayload(ALICE_UID, { category: "RANDOM" }))
  );

  // Reject invalid filedDateIso format
  await assertFails(
    docsCol.doc("doc_bad_date").set(validDocumentPayload(ALICE_UID, { filedDateIso: "12/1/2026" }))
  );

  // Reject future timestamp
  await assertFails(
    docsCol.doc("doc_future").set(
      validDocumentPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Seed valid doc then reject immutable field updates (createdAt / userId)
  await assertSucceeds(docsCol.doc("doc_valid").set(validDocumentPayload(ALICE_UID)));

  await assertFails(
    docsCol.doc("doc_valid").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    docsCol.doc("doc_valid").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mixed action (cannot toggle star AND edit title in same action)
  await assertFails(
    docsCol.doc("doc_valid").update({
      title: "Sneaky Edit",
      starred: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, write, or delete Alice's documents", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("alice_doc_1")
      .set(validDocumentPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceColAsBob = bobDb.collection("users").doc(ALICE_UID).collection("documents");

  await assertFails(aliceColAsBob.get());
  await assertFails(aliceColAsBob.doc("alice_doc_1").get());
  await assertFails(aliceColAsBob.doc("bob_spoof").set(validDocumentPayload(BOB_UID)));
  await assertFails(
    aliceColAsBob.doc("alice_doc_1").update({
      starred: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceColAsBob.doc("alice_doc_1").delete());
});
