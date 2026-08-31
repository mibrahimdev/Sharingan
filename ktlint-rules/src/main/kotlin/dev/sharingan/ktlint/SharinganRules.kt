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

/**
 * Flags multi-line comments:
 * - a run of consecutive `//` lines (comment continuation), and
 * - a `/* ... */` block comment spanning more than one line.
 *
 * Per the AGENTS.md comments policy, if it doesn't fit on one line it is
 * either not worth saying or it is documentation — and documentation is
 * KDoc (`/** ... */`), which this rule leaves untouched.
 */
class NoMultiLineCommentRule :
    Rule(
        RuleId("$SHARINGAN_RULE_SET_ID:no-multi-line-comment"),
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
        when (node.elementType) {
            ElementType.EOL_COMMENT -> if (isContinuation(node)) {
                emit(
                    node.startOffset,
                    "Multi-line comment: keep it to one line, or use KDoc if it is documentation " +
                        "(AGENTS.md comments policy)",
                    false,
                )
            }
            ElementType.BLOCK_COMMENT -> if (node.text.contains('\n')) {
                emit(
                    node.startOffset,
                    "Multi-line block comment: keep it to one line, or use KDoc if it is documentation " +
                        "(AGENTS.md comments policy)",
                    false,
                )
            }
        }
    }

    /**
     * True when the next non-whitespace sibling is another `//` comment on the
     * immediately following line. The whitespace between them must hold exactly
     * one newline: a blank line or code in between breaks the "run".
     */
    private fun isContinuation(node: ASTNode): Boolean {
        var next = node.treeNext
        while (next != null && next.elementType == ElementType.WHITE_SPACE) next = next.treeNext
        if (next == null || next.elementType != ElementType.EOL_COMMENT) return false
        if (next.treePrev.elementType != ElementType.WHITE_SPACE) return false
        return next.treePrev.text.count { it == '\n' } == 1
    }
}

class SharinganRuleSetProvider :
    RuleSetProviderV3(RuleSetId(SHARINGAN_RULE_SET_ID)) {
    // getRuleProviders is deprecated in ktlint's API but remains the abstract
    // member in 1.5.x — there is no replacement to migrate to on this version.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { TodoWithoutIssueRule() },
            RuleProvider { NoNarrationCommentRule() },
            RuleProvider { NoMultiLineCommentRule() },
        )
}
