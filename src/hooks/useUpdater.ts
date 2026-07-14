// useUpdater — Tauri auto-updater integration (FP-10.2)
// Wraps @tauri-apps/plugin-updater to provide manual/auto update checks.

import { check, type Update, type DownloadEvent } from "@tauri-apps/plugin-updater";

/** Result of a manual update check. */
export interface UpdateInfo {
  available: boolean;
  version: string;
  currentVersion: string;
  date?: string;
  body?: string;
}

/** Check result including the live Update handle needed for installation. */
export interface UpdateResult {
  info: UpdateInfo;
  update: Update;
}

/** Check for a pending update. Returns null if already on latest. */
export async function checkForUpdate(): Promise<UpdateResult | null> {
  try {
    const update = await check();
    if (!update) return null;
    return {
      info: {
        available: true,
        version: update.version,
        currentVersion: update.currentVersion,
        date: update.date,
        body: update.body,
      },
      update,
    };
  } catch (e) {
    console.error("[useUpdater] check failed:", e);
    return null;
  }
}

function makeProgressHandler(
  onProgress?: (percent: number) => void
): (event: DownloadEvent) => void {
  let total: number | undefined;
  let downloaded = 0;
  return (event) => {
    if (event.event === "Started") {
      total = event.data.contentLength;
      downloaded = 0;
      onProgress?.(0);
    } else if (event.event === "Progress") {
      downloaded += event.data.chunkLength;
      if (total && total > 0) {
        onProgress?.(Math.min(100, Math.round((downloaded / total) * 100)));
      } else {
        onProgress?.(Math.min(99, Math.round(downloaded / 1024 / 1024)));
      }
    } else if (event.event === "Finished") {
      onProgress?.(100);
    }
  };
}

/** Download and install the given update, calling onProgress with 0..100. */
export async function installUpdate(
  update: Update,
  onProgress?: (percent: number) => void
): Promise<void> {
  await update.downloadAndInstall(makeProgressHandler(onProgress));
}

/** Auto-check for updates after a delay, best-effort. */
export function scheduleAutoUpdateCheck(
  delayMs: number,
  onUpdate: (result: UpdateResult) => void
): () => void {
  const timer = setTimeout(async () => {
    const result = await checkForUpdate();
    if (result) onUpdate(result);
  }, delayMs);
  return () => clearTimeout(timer);
}
