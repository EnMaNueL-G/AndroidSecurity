# AndroidSecurity

**Auditoría de permisos, detección de amenazas y guard de cámara/micrófono para Android — sin root, sin servidores.**

[![Release](https://img.shields.io/github/v/release/EnMaNueL-G/AndroidSecurity?color=5B8DEF)](https://github.com/EnMaNueL-G/AndroidSecurity/releases)
[![Android](https://img.shields.io/badge/Android-8%2B-3DDC84)](https://github.com/EnMaNueL-G/AndroidSecurity)
[![License](https://img.shields.io/badge/License-MIT-7C5CEF)](LICENSE)

---

## ¿Qué hace?

| Pantalla | Función |
|----------|---------|
| **Permisos** | Muestra todas las apps instaladas con sus permisos clasificados por riesgo (alto / medio / bajo). Filtra por categoría. |
| **Detector** | Detecta servicios de accesibilidad activos, administradores de dispositivo y combinaciones peligrosas de permisos (ej. cámara + micrófono juntos). |
| **Guard** | Registra qué apps accedieron a la cámara o al micrófono en los últimos 30 días, con fecha y hora exactas. |
| **Historial** | Timeline de accesos a permisos sensibles (cámara, micrófono, ubicación, SMS, contactos) con ventanas de 1h, 6h, 24h o 7 días. |

---

## Características

- **Sin root** — usa las APIs públicas de Android: `AppOpsManager`, `DevicePolicyManager`, `Settings.Secure`
- **Sin internet** — 100% local, ningún dato sale del dispositivo
- **Multiidioma** — Español · English · Português · Français · Deutsch
- **Android 8+** (API 26+) compatible con Android 14
- Interfaz oscura con Material 3 y Jetpack Compose

---

## Capturas

> *APK disponible en Releases*

---

## Instalación

1. Descarga el APK desde [Releases](https://github.com/EnMaNueL-G/AndroidSecurity/releases/latest)
2. Activa "Instalar apps desconocidas" en Ajustes → Seguridad
3. Abre el APK e instala
4. Concede el permiso **"Acceso al uso de aplicaciones"** cuando se solicite (necesario para Guard e Historial)

---

## Compilar desde el código fuente

```bash
git clone https://github.com/EnMaNueL-G/AndroidSecurity.git
cd AndroidSecurity
./gradlew assembleRelease
```

Requiere Android Studio Hedgehog (2023.1.1) o superior, JDK 17.

---

## Permisos requeridos

| Permiso | Motivo |
|---------|--------|
| `QUERY_ALL_PACKAGES` | Listar todas las apps instaladas |
| `PACKAGE_USAGE_STATS` | Leer historial de accesos a cámara/micrófono (AppOps) |
| `POST_NOTIFICATIONS` | Notificación del servicio Guard en segundo plano |
| `FOREGROUND_SERVICE` | Mantener el Guard activo con notificación persistente |

---

## Changelog

### v1.0.0 — 2026-06-07
- Primera versión pública
- Auditor de permisos con clasificación de riesgo
- Detector de amenazas: accesibilidad, device admin, combos peligrosos
- Guard de cámara/micrófono con historial de 30 días
- Historial de accesos con filtro de tiempo
- Multiidioma: es / en / pt / fr / de
- Servicio en segundo plano `CamMicGuardService`

---

## Parte de OptiSuite

AndroidSecurity es parte del ecosistema [OptiSuite](https://optisuite.app) — herramientas gratuitas de optimización y seguridad.

---

## Licencia

MIT © [EnMaNueL-G](https://github.com/EnMaNueL-G)
