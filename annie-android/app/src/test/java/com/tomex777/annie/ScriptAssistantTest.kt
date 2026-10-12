package com.tomex777.annie

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptAssistantTest {
    @Test fun previewValidationRunsBeforeAProposalCanApply() {
        assertFalse(ScriptAssistValidator.validate("").canApply)
        assertTrue(
            ScriptAssistValidator.validate(
                "annie.commands.register({ name: \"hello\", async execute() { return { type: \"text\", text: \"hi\" }; } });"
            ).canApply
        )
    }

    @Test fun diffShowsRemovedAndAddedSource() {
        val diff = buildScriptAssistDiff("const x = 1;\nkeep();", "const x = 2;\nkeep();")
        assertTrue("- const x = 1;" in diff)
        assertTrue("+ const x = 2;" in diff)
    }

    @Test fun providerBoundaryExposesClaudeWithoutCouplingCallersToItsClass() {
        val assistant = ScriptAssistant()
        assertTrue(assistant.availableProviders().any { it.id == "anthropic" })
    }
    @Test fun documentAssistancePreservesLanguageAndRuntimeLimits() {
        assertTrue(scriptAssistSystemPrompt("pages/index.html").contains("Do not turn an HTML or CSS"))
        assertTrue(scriptAssistSystemPrompt("theme.css").contains("requested css file"))
        assertTrue(scriptAssistSystemPrompt("main.js").contains("QuickJS runtime"))
        assertTrue(scriptAssistSystemPrompt("scripts/app.js", webProject = true).contains("classic browser JavaScript"))
        assertTrue(scriptAssistSystemPrompt("worker.py").contains("does not currently execute py directly"))
    }

}
