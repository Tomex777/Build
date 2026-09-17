import { defineCommand, defineModule } from "../../core/module";
import { setting } from "../../shared/config-schema";

export const coreModule = defineModule({
  id: "core",
  name: "Core",
  version: "0.2.0",
  description: "Base Bailey Host behaviour and bot defaults.",
  settings: [
    setting.text("prefix", "Command prefix", ".", {
      env: "BAILEY_PREFIX",
      description: "Prefix used before commands, for example .status.",
      placeholder: ".",
    }),
    setting.text("owner", "Owner number / JID", "", {
      env: "BAILEY_OWNER",
      description: "Optional owner identity used for owner-only commands.",
      placeholder: "234…",
    }),
    setting.toggle("autoStart", "Start Bailey when Windows starts", true, {
      env: "BAILEY_AUTOSTART",
      description: "Keep the host available after a restart without opening it manually.",
    }),
    setting.select("mediaPolicy", "Media download policy", "manual", [
      { label: "Manual only", value: "manual" },
      { label: "Wi-Fi only", value: "wifi-only" },
      { label: "Always", value: "always" },
    ], {
      env: "BAILEY_MEDIA_POLICY",
      description: "Manual is the low-data default: media is downloaded only when a command requests it.",
    }),
    setting.number("maxMediaMb", "Maximum media size", 25, {
      env: "BAILEY_MAX_MEDIA_MB",
      description: "Upper size limit for media fetched by commands.",
      min: 1,
      max: 2048,
      step: 1,
    }),
    setting.select("logLevel", "Log level", "info", [
      { label: "Error", value: "error" },
      { label: "Warn", value: "warn" },
      { label: "Info", value: "info" },
      { label: "Debug", value: "debug" },
    ], {
      env: "BAILEY_LOG_LEVEL",
      description: "How much detail Bailey Host writes to its local logs.",
    }),
  ],
  commands: [
    defineCommand({
      name: "status",
      description: "Show that Bailey Host and the WhatsApp engine are responsive.",
      async execute(ctx) {
        await ctx.reply("Bailey Host is online.");
      },
    }),
    defineCommand({
      name: "ping",
      description: "Check that the bot runtime is responsive.",
      aliases: ["p"],
      async execute(ctx) {
        await ctx.reply("Pong.");
      },
    }),
  ],
});
