import { app } from "electron";
import { join } from "node:path";
import "./main";
import { ModuleStudioController } from "./module-studio-controller";

app.whenReady().then(() => {
  const modulesRoot = join(app.getPath("userData"), "modules");
  new ModuleStudioController(modulesRoot).registerIpc();
});
