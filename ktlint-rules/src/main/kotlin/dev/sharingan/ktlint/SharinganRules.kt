package dev.sharingan.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment

// ponytail: heuristics — these rules catch the common AI narration patterns
// and bare TODOs; they cannot judge genuine usefulness. If a false positive
// appears in legit code, refine the word list or use a `// ktlint-disable`.
const val SHARINGAN_RULE_SET_ID = "sharingan-comments"

/**
 * `TODO` / `FIXME` comments must reference an issue: `TODO(#123): ...`.
 * Enforces the AGENTS.md comments policy — an unowned TODO is a promise
 * nobody will keep.
 */
class TodoWithoutIssueRule :
    Rule(
        RuleId("$SHARINGAN_RULE_SET_ID:todo-without-issue"),
        Rule.About(
            maintainer = "Sharingan maintainers",
            repositoryUrl = "https://github.com/mibrahimdev/Sharingan",
            issueTrackerUrl = "https://github.com/mibrahimdev/Sharingan/issues",
        ),
    ) {
    private val unownedTodo = Regex("\\b(TODO|FIXME)\\b(?!\\s*\\(\\s*#\\d+\\s*\\))")

    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType != ElementType.EOL_COMMENT && node.elementType != ElementType.BLOCK_COMMENT) return
        val text = (node.psi as? PsiComment)?.text ?: return
        if (text.contains("ktlint-disable") || text.contains("ktlint-enable")) return
        if (unownedTodo.containsMatchIn(text)) {
            emit(
                node.startOffset,
                "TODO/FIXME must reference an issue: 'TODO(#123): ...' (AGENTS.md comments policy)",
                false,
            )
        }
    }
}

/**
 * Flags narration comments — the AI-generated "what I just did" notes:
 * `// added null check`, `// removed unused import`, `// refactored ...`.
 * Per the AGENTS.md comments policy, comments explain *why*, never
 * narrate *what* or the change history (git log does that).
 */
class NoNarrationCommentRule :
    Rule(
        RuleId("$SHARINGAN_RULE_SET_ID:no-narration-comment"),
        Rule.About(
            maintainer = "Sharingan maintainers",
            repositoryUrl = "https://github.com/mibrahimdev/Sharingan",
            issueTrackerUrl = "https://github.com/mibrahimdev/Sharingan/issues",
        ),
    ) {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType != ElementType.EOL_COMMENT && node.elementType != ElementType.BLOCK_COMMENT) return
        val comment = node.psi as? PsiComment ?: return
        val text = comment.text
        if (text.contains("ktlint-disable") || text.contains("ktlint-enable")) return
        val body = text
            .removePrefix("//")
            .removePrefix("/*")
            .trim()
        if (NARRATION_START.containsMatchIn(body)) {
            emit(
                node.startOffset,
                "Narration comment: comments explain *why*, not what was just changed " +
                    "(AGENTS.md comments policy). Delete it or state the invariant/reason.",
                false,
            )
        }
    }

    private companion object {
        // Past-tense change verbs + classic AI note openers. Deliberately
        // conservative: present-tense instructions (`// add the item ...`)
        // are often legitimate and are not flagged.
        val NARRATION_START = Regex(
            "^(?:added|removed|fixed|updated|changed|refactored|renamed|moved|" +
                "implemented|extracted|replaced|deprecated|bumped|adjusted|modified|" +
                "addressed|cleaned(?:\\s+up)?|new\\b|now\\b)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}

class SharinganRuleSetProvider :
    RuleSetProviderV3(RuleSetId(SHARINGAN_RULE_SET_ID)) {
    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { TodoWithoutIssueRule() },
            RuleProvider { NoNarrationCommentRule() },
        )
}
