#ifndef TS_QUERY_FILTER_H
#define TS_QUERY_FILTER_H

#include <stdint.h>
#include <string>
#include <tree_sitter/api.h>

// Decides, before they are handed to Kotlin, the text and node predicates of the
// matches, the way Neovim does as the matches come (it removes the failed ones from the
// cursor). A match whose predicates fail is left out; one whose predicates all hold, every
// one of them decided here, comes flagged (TSQueryMatch.predicatesDecided) and Kotlin
// does not evaluate them again. Each match handed to Kotlin and evaluated there costs a
// list, nodes, a capture, regexes, and calls back into the VM: a third of the time a block
// of a large C file took to be highlighted.
//
// Decided here, with the semantics of the Kotlin predicates (TSQueryPredicate,
// QueryPredicates), when the cursor was given the text (setText):
//
//  - eq? / not-eq? / any-eq? / any-not-eq?, with a string or another capture;
//  - any-of? / not-any-of?;
//  - lua-match? and its not- / any- forms: Lua patterns in full (classes, sets,
//    quantifiers, anchors, captures, back-references, %b, %f), as Lua 5.1 / LuaJIT match
//    them (string.find, which Neovim uses), on the UTF-8 bytes of the text;
//  - contains?, has-ancestor?, has-parent?, kind-eq? and their not- / any- forms;
//  - directives (set!, offset!, ...) and predicates Kotlin does not know: they hold.
//
// Not decided here: match? / vim-match? (Vim regexes), a capture whose text is not in the
// text given, a pattern Lua would not take. Such a match is only left out if another of
// its predicates fails, and is otherwise decided in Kotlin as before.
namespace query_filter {

// the query the cursor runs (at exec)
void set_query(const TSQueryCursor *cursor, const TSQuery *query);
// the text of the document (UTF-16) from start_byte on, for the cursor
void set_text(const TSQueryCursor *cursor, uint32_t start_byte, std::u16string &&text);
// the cursor or the query is deleted
void forget_cursor(const TSQueryCursor *cursor);
void forget_query(const TSQuery *query);

enum class Decision {
    // a predicate is not one of those decided here, or cannot be for this match
    UNDECIDED,
    // a predicate fails: the match is left out
    REJECT,
    // every predicate holds, all decided here
    ACCEPT
};

// what the predicates of the pattern of [match] say
Decision decide(const TSQueryCursor *cursor, const TSQueryMatch &match);

// The Lua pattern matcher (string.find(text, pattern) ~= nil), on bytes. [error] is set
// (and false returned) for a pattern Lua would raise an error for.
bool lua_find(const char *text, size_t length, const char *pattern, bool *error);

} // namespace query_filter

#endif // TS_QUERY_FILTER_H
