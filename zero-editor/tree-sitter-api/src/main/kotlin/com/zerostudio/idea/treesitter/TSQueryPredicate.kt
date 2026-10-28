package com.zerostudio.idea.treesitter

/**
 * A query [predicate](https://tree-sitter.github.io/tree-sitter/using-parsers#predicates)
 * that associates conditions or arbitrary metadata with a pattern.
 *
 * The following predicates are supported by default:
 *
 * - `#eq?`, `#not-eq?`, `#any-eq?`, `#any-not-eq?`
 * - `#match?`, `#not-match?`, `#any-match?`, `#any-not-match?`
 * - `#any-of?`, `#not-any-of?`
 *
 * @property name The name of the predicate.
 * @property args The arguments given to the predicate.
 */

sealed class TSQueryPredicate(val name: String) {
    abstract val args: List<TSQueryPredicateArgs>

    internal abstract operator fun invoke(match: TSQueryMatch): Boolean

    final override fun toString() = "TSQueryPredicate(name=$name, args=$args)"

    internal class EqCapture(
        name: String,
        private val capture: String,
        private val value: String,
        private val isPositive: Boolean,
        private val isAny: Boolean
    ) : TSQueryPredicate(name) {
        override val args = listOf(
            TSQueryPredicateArgs.Capture(capture),
            TSQueryPredicateArgs.Capture(value)
        )

        override fun invoke(match: TSQueryMatch): Boolean {
            val nodes1 = match[capture]
            val nodes2 = match[value]
            val test = if (!isAny) nodes1::all else nodes1::any
            return test { n1 ->
                nodes2.any { n2 ->
                    match.predicateResult = n1.text() == n2.text()
                    if (isPositive) match.predicateResult else !match.predicateResult
                }
            }
        }
    }

    internal class EqString(
        name: String,
        private val capture: String,
        private val value: String,
        private val isPositive: Boolean,
        private val isAny: Boolean
    ) : TSQueryPredicate(name) {
        override val args = listOf(
            TSQueryPredicateArgs.Capture(capture),
            TSQueryPredicateArgs.Literal(value)
        )

        override fun invoke(match: TSQueryMatch): Boolean {
            val nodes = match[capture]
            if (nodes.isEmpty()) return !isPositive
            val test = if (!isAny) nodes::all else nodes::any
            return test {
                match.predicateResult = value == it.text()!!
                if (isPositive) match.predicateResult else !match.predicateResult
            }
        }
    }

    internal class Match(
        name: String,
        private val capture: String,
        private val pattern: Regex,
        private val isPositive: Boolean,
        private val isAny: Boolean
    ) : TSQueryPredicate(name) {
        override val args = listOf(
            TSQueryPredicateArgs.Capture(capture),
            TSQueryPredicateArgs.Literal(pattern.pattern)
        )

        override fun invoke(match: TSQueryMatch): Boolean {
            val nodes = match[capture]
            if (nodes.isEmpty()) return !isPositive
            val test = if (!isAny) nodes::all else nodes::any            
            return test {               
                match.predicateResult = pattern.containsMatchIn(it.text()!!)                
                if (isPositive) match.predicateResult else !match.predicateResult
            }
        }
    }

    internal class AnyOf(
        name: String,
        private val capture: String,
        private val value: List<String>,
        private val isPositive: Boolean
    ) : TSQueryPredicate(name) {
        override val args = List(value.size + 1) {
            if (it == 0) TSQueryPredicateArgs.Capture(capture)
            else TSQueryPredicateArgs.Literal(value[it - 1])
        }

        override fun invoke(match: TSQueryMatch): Boolean {
            match.predicateResult = match[capture].none { 
                (it.text()!! in value) != isPositive 
            }
            return match.predicateResult
        }
    }

    internal class Generic(
        name: String,
        override val args: List<TSQueryPredicateArgs>
    ) : TSQueryPredicate(name) {
        override fun invoke(match: TSQueryMatch) = true
    }
}

