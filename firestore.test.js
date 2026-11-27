const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_001";
const OWNER_EMAIL = "cm2026enero@gmail.com";
const ADMIN_UID = "admin_uid_002";
const DRIVER_UID = "driver_uid_003";
const OTHER_UID = "other_uid_004";
const VALID_INVITE_HASH = "a".repeat(64);

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ownerDb() {
  return testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
}

function spoofedOwnerDb() {
  return testEnv.authenticatedContext(OTHER_UID, {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
}

function adminDb() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: "admin@anthi.local",
    email_verified: true,
  }).firestore();
}

function driverDb() {
  return testEnv.authenticatedContext(DRIVER_UID, {
    email: "driver07@anthi.local",
    email_verified: true,
  }).firestore();
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

test("Usuario no autenticado no puede leer ni escribir colecciones", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("events").where("status", "==", "active").get());
  await assertFails(unauthDb.collection("users").doc(DRIVER_UID).get());
  await assertFails(unauthDb.collection("admins").doc(ADMIN_UID).get());
});

test("Email Spoofing: correo de dueño sin email_verified=true es rechazado", async () => {
  const past = new Date(Date.now() - 2000);
  await assertFails(
    spoofedOwnerDb().collection("admin_invites").doc(VALID_INVITE_HASH).set({
      label: "Falso",
      active: true,
      createdBy: OTHER_UID,
      createdAt: past,
    })
  );
});

test("Jerarquía: Superadmin crea código de invitación y Admin se habilita con hash válido", async () => {
  const past = new Date(Date.now() - 2000);

  // Conductor no puede crear códigos de invitación
  await assertFails(
    driverDb().collection("admin_invites").doc(VALID_INVITE_HASH).set({
      label: "No autorizado",
      active: true,
      createdBy: DRIVER_UID,
      createdAt: past,
    })
  );

  // Superadmin crea invitación
  await assertSucceeds(
    ownerDb().collection("admin_invites").doc(VALID_INVITE_HASH).set({
      label: "Central Nocturna",
      active: true,
      createdBy: OWNER_UID,
      createdAt: past,
    })
  );

  // Nadie no-dueño puede listar invitaciones
  await assertFails(driverDb().collection("admin_invites").get());

  // Con código inválido no puede registrarse como Admin
  await assertFails(
    adminDb().collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "admin@anthi.local",
      alias: "Central M34",
      role: "ADMIN",
      inviteHash: "f".repeat(64),
      grantedAt: past,
    })
  );

  // Con hash válido sí se habilita como ADMIN
  await assertSucceeds(
    adminDb().collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "admin@anthi.local",
      alias: "Central M34",
      role: "ADMIN",
      inviteHash: VALID_INVITE_HASH,
      grantedAt: past,
    })
  );

  // Un Admin no puede modificar ni revocar permisos de admins
  await assertFails(
    adminDb().collection("admins").doc(ADMIN_UID).update({ alias: "Super" })
  );
  await assertFails(
    adminDb().collection("admins").doc(ADMIN_UID).delete()
  );
});

test("Eventos: Emisor SOS puede activar evento, Admin puede cerrar, y SOLO Superadministrador/Dueño puede eliminar", async () => {
  const past = new Date(Date.now() - 3000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "admin@anthi.local",
      alias: "Central M34",
      role: "ADMIN",
      inviteHash: VALID_INVITE_HASH,
      grantedAt: past,
    });
  });

  // Emisor autenticado (escudo M34) puede activar evento SOS con active=true y status=active
  await assertSucceeds(
    driverDb().collection("events").doc("ev_sos").set({
      eventNumber: 3,
      code: "ANTHI-SOS-3",
      callsign: "Móvil 07",
      sector: "Café Madrid · Alarma M34",
      lat: 7.1510,
      lng: -73.1310,
      bearing: 0,
      speedKmh: 0,
      audioDb: 72,
      active: true,
      status: "active",
      createdBy: DRIVER_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Conductor NO puede cerrar ni eliminar un evento activo (solo Admin/Superadmin)
  await assertFails(
    driverDb().collection("events").doc("ev_sos").update({
      active: false,
      status: "closed",
      closedBy: DRIVER_UID,
      closedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(driverDb().collection("events").doc("ev_sos").delete());

  // Admin crea evento activo
  await assertSucceeds(
    adminDb().collection("events").doc("ev_1").set({
      eventNumber: 1,
      code: "ANTHI-3401",
      callsign: "Móvil 14",
      sector: "Café Madrid",
      lat: 7.1524,
      lng: -73.1296,
      bearing: 12,
      speedKmh: 54,
      audioDb: 66,
      active: true,
      status: "active",
      createdBy: ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Consulta filtrada por active == true funciona para conductores; consulta sin filtro falla
  await assertSucceeds(
    driverDb().collection("events").where("active", "==", true).get()
  );
  await assertFails(driverDb().collection("events").get());

  // Conductor confirma "Sí voy"
  await assertSucceeds(
    driverDb().collection("events").doc("ev_1").collection("responders").doc(DRIVER_UID).set({
      userId: DRIVER_UID,
      eventId: "ev_1",
      callsign: "Móvil 07",
      lat: 7.1462,
      lng: -73.1332,
      etaMin: 3,
      active: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin NO puede eliminar el evento
  await assertFails(adminDb().collection("events").doc("ev_1").delete());

  // Admin SÍ puede cerrarlo (active -> false, status -> closed)
  await assertSucceeds(
    adminDb().collection("events").doc("ev_1").update({
      active: false,
      status: "closed",
      closedBy: ADMIN_UID,
      closedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Una vez cerrado, Admin no puede reabrirlo ni editarlo
  await assertFails(
    adminDb().collection("events").doc("ev_1").update({
      active: true,
      status: "active",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Superadministrador / Dueño SÍ puede eliminar definitivamente ambos eventos
  await assertSucceeds(ownerDb().collection("events").doc("ev_1").delete());
  await assertSucceeds(ownerDb().collection("events").doc("ev_sos").delete());
});

test("Perfiles de usuario: aislamiento estricto por UID", async () => {
  const past = new Date(Date.now() - 2000);
  await assertSucceeds(
    driverDb().collection("users").doc(DRIVER_UID).set({
      userId: DRIVER_UID,
      anthiUid: "anthi_movil_07",
      alias: "Móvil 07",
      email: "driver07@anthi.local",
      focusedEvent: 1,
      confirmedEvent: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(adminDb().collection("users").doc(DRIVER_UID).get());
  await assertFails(
    driverDb().collection("users").doc(DRIVER_UID).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
