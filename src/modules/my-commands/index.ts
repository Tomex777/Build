import { defineModule } from "../../core/module";

/**
 * User-authored visual commands live here. The actual commands are persisted
 * outside the packaged app so Bailey Host upgrades never overwrite them.
 */
export const myCommandsModule = defineModule({
  id: "my-commands",
  name: "My Commands",
  version: "1.0.0",
  description: "Commands you create in Bailey Studio without writing code.",
  commands: [],
});
