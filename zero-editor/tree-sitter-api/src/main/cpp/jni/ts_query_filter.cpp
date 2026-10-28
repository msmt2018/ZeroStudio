#include "ts_query_filter.h"

#include <memory>
#include <mutex>
#include <string.h>
#include <unordered_map>
#include <vector>

namespace query_filter {

// #region Lua patterns

// A port of the matcher of Lua 5.1's lstrlib.c, as LuaJIT has it (lib_string.c), which
// string.find uses in Neovim: the same steps, on the same bytes. Errors are reported in
// MatchState::error rather than raised.
namespace {

constexpr char L_ESC = '%';
constexpr int MAX_CAPTURES = 32;
constexpr int CAP_UNFINISHED = -1;
constexpr int CAP_POSITION = -2;
// deeper than this (a pathological pattern): given up, as an error
constexpr int MAX_DEPTH = 400;

struct MatchState {
    const char *src_init;
    const char *src_end;
    int level;
    int depth;
    bool error;
    struct {
        const char *init;
        ptrdiff_t len;
    } capture[MAX_CAPTURES];
};

// the character classes of LuaJIT (lj_char), which are ASCII only
inline bool is_alpha(int c) { return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'); }
inline bool is_digit(int c) { return c >= '0' && c <= '9'; }
inline bool is_lower(int c) { return c >= 'a' && c <= 'z'; }
inline bool is_upper(int c) { return c >= 'A' && c <= 'Z'; }
inline bool is_space(int c) { return c == ' ' || (c >= '\t' && c <= '\r'); }
inline bool is_cntrl(int c) { return c < 32 || c == 127; }
inline bool is_punct(int c) { return c >= 33 && c <= 126 && !is_alpha(c) && !is_digit(c); }
inline bool is_xdigit(int c) { return is_digit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
inline bool is_graph(int c) { return c >= 0x21 && c <= 0x7E; }
inline int to_lower(int c) { return is_upper(c) ? c + 32 : c; }

const char *match(MatchState *ms, const char *s, const char *p);

int check_capture(MatchState *ms, int l) {
    l -= '1';
    if (l < 0 || l >= ms->level || ms->capture[l].len == CAP_UNFINISHED) {
        ms->error = true;
        return 0;
    }
    return l;
}

int capture_to_close(MatchState *ms) {
    int level = ms->level;
    for (level--; level >= 0; level--) {
        if (ms->capture[level].len == CAP_UNFINISHED) return level;
    }
    ms->error = true;
    return 0;
}

const char *class_end(MatchState *ms, const char *p) {
    switch (*p++) {
        case L_ESC:
            if (*p == '\0') {
                ms->error = true;
                return p;
            }
            return p + 1;
        case '[':
            if (*p == '^') p++;
            do {
                if (*p == '\0') {
                    ms->error = true;
                    return p;
                }
                if (*(p++) == L_ESC && *p != '\0') p++;
            } while (*p != ']');
            return p + 1;
        default:
            return p;
    }
}

bool match_class(int c, int cl) {
    bool res;
    switch (to_lower(cl)) {
        case 'a': res = is_alpha(c); break;
        case 'c': res = is_cntrl(c); break;
        case 'd': res = is_digit(c); break;
        case 'g': res = is_graph(c); break;
        case 'l': res = is_lower(c); break;
        case 'p': res = is_punct(c); break;
        case 's': res = is_space(c); break;
        case 'u': res = is_upper(c); break;
        case 'w': res = is_alpha(c) || is_digit(c); break;
        case 'x': res = is_xdigit(c); break;
        case 'z': res = (c == 0); break;
        default: return cl == c;
    }
    return is_upper(cl) ? !res : res;
}

bool match_bracket_class(int c, const char *p, const char *ec) {
    bool sig = true;
    if (*(p + 1) == '^') {
        sig = false;
        p++;
    }
    while (++p < ec) {
        if (*p == L_ESC) {
            p++;
            if (match_class(c, (unsigned char)*p)) return sig;
        } else if (*(p + 1) == '-' && p + 2 < ec) {
            p += 2;
            if ((unsigned char)*(p - 2) <= c && c <= (unsigned char)*p) return sig;
        } else if ((unsigned char)*p == c) {
            return sig;
        }
    }
    return !sig;
}

bool single_match(int c, const char *p, const char *ep) {
    switch (*p) {
        case '.': return true;
        case L_ESC: return match_class(c, (unsigned char)*(p + 1));
        case '[': return match_bracket_class(c, p, ep - 1);
        default: return (unsigned char)*p == c;
    }
}

const char *match_balance(MatchState *ms, const char *s, const char *p) {
    if (*p == '\0' || *(p + 1) == '\0') {
        ms->error = true;
        return nullptr;
    }
    if (s >= ms->src_end || *s != *p) return nullptr;
    int b = *p, e = *(p + 1), cont = 1;
    while (++s < ms->src_end) {
        if (*s == e) {
            if (--cont == 0) return s + 1;
        } else if (*s == b) {
            cont++;
        }
    }
    return nullptr;
}

const char *max_expand(MatchState *ms, const char *s, const char *p, const char *ep) {
    ptrdiff_t i = 0;
    while (s + i < ms->src_end && single_match((unsigned char)*(s + i), p, ep)) i++;
    while (i >= 0) {
        const char *res = match(ms, s + i, ep + 1);
        if (res != nullptr || ms->error) return res;
        i--;
    }
    return nullptr;
}

const char *min_expand(MatchState *ms, const char *s, const char *p, const char *ep) {
    for (;;) {
        const char *res = match(ms, s, ep + 1);
        if (res != nullptr || ms->error) return res;
        if (s < ms->src_end && single_match((unsigned char)*s, p, ep)) s++;
        else return nullptr;
    }
}

const char *start_capture(MatchState *ms, const char *s, const char *p, int what) {
    int level = ms->level;
    if (level >= MAX_CAPTURES) {
        ms->error = true;
        return nullptr;
    }
    ms->capture[level].init = s;
    ms->capture[level].len = what;
    ms->level = level + 1;
    const char *res = match(ms, s, p);
    if (res == nullptr) ms->level--;
    return res;
}

const char *end_capture(MatchState *ms, const char *s, const char *p) {
    int l = capture_to_close(ms);
    if (ms->error) return nullptr;
    ms->capture[l].len = s - ms->capture[l].init;
    const char *res = match(ms, s, p);
    if (res == nullptr) ms->capture[l].len = CAP_UNFINISHED;
    return res;
}

const char *match_capture(MatchState *ms, const char *s, int l) {
    l = check_capture(ms, l);
    if (ms->error) return nullptr;
    size_t len = (size_t)ms->capture[l].len;
    if ((size_t)(ms->src_end - s) >= len && memcmp(ms->capture[l].init, s, len) == 0) return s + len;
    return nullptr;
}

// one level deeper while it is in [match]
struct Depth {
    MatchState *ms;
    explicit Depth(MatchState *state) : ms(state) {
        if (++ms->depth > MAX_DEPTH) ms->error = true;
    }
    ~Depth() { ms->depth--; }
};

const char *match(MatchState *ms, const char *s, const char *p) {
    Depth depth(ms);
    if (ms->error) return nullptr;
init:
    switch (*p) {
        case '(':
            if (*(p + 1) == ')') return start_capture(ms, s, p + 2, CAP_POSITION);
            return start_capture(ms, s, p + 1, CAP_UNFINISHED);
        case ')':
            return end_capture(ms, s, p + 1);
        case L_ESC:
            switch (*(p + 1)) {
                case 'b':
                    s = match_balance(ms, s, p + 2);
                    if (s == nullptr) return nullptr;
                    p += 4;
                    goto init;
                case 'f': {
                    p += 2;
                    if (*p != '[') {
                        ms->error = true;
                        return nullptr;
                    }
                    const char *ep = class_end(ms, p);
                    if (ms->error) return nullptr;
                    int previous = (s == ms->src_init) ? '\0' : (unsigned char)*(s - 1);
                    // at the end: the '\0' after the text, as Lua strings have
                    int current = (s < ms->src_end) ? (unsigned char)*s : '\0';
                    if (match_bracket_class(previous, p, ep - 1) || !match_bracket_class(current, p, ep - 1)) {
                        return nullptr;
                    }
                    p = ep;
                    goto init;
                }
                default:
                    if (is_digit((unsigned char)*(p + 1))) {
                        s = match_capture(ms, s, (unsigned char)*(p + 1));
                        if (s == nullptr) return nullptr;
                        p += 2;
                        goto init;
                    }
                    goto dflt;
            }
        case '\0':
            return s;
        case '$':
            if (*(p + 1) == '\0') return (s == ms->src_end) ? s : nullptr;
            goto dflt;
        default:
        dflt: {
            const char *ep = class_end(ms, p);
            if (ms->error) return nullptr;
            bool m = s < ms->src_end && single_match((unsigned char)*s, p, ep);
            switch (*ep) {
                case '?': {
                    if (m) {
                        const char *res = match(ms, s + 1, ep + 1);
                        if (res != nullptr || ms->error) return res;
                    }
                    p = ep + 1;
                    goto init;
                }
                case '*':
                    return max_expand(ms, s, p, ep);
                case '+':
                    return m ? max_expand(ms, s + 1, p, ep) : nullptr;
                case '-':
                    return min_expand(ms, s, p, ep);
                default:
                    if (!m) return nullptr;
                    s++;
                    p = ep;
                    goto init;
            }
        }
    }
}

} // namespace

bool lua_find(const char *text, size_t length, const char *pattern, bool *error) {
    // Without any special char, string.find looks for the pattern as it is (a ')' or a
    // ']' alone is a char like the others there).
    if (strpbrk(pattern, "^$*+?.([%-") == nullptr) {
        size_t n = strlen(pattern);
        if (n == 0) return true;
        if (n > length) return false;
        for (size_t i = 0; i + n <= length; i++) {
            if (memcmp(text + i, pattern, n) == 0) return true;
        }
        return false;
    }
    MatchState ms{};
    ms.src_init = text;
    ms.src_end = text + length;
    const char *p = pattern;
    bool anchor = (*p == '^');
    if (anchor) p++;
    const char *s1 = text;
    do {
        ms.level = 0;
        ms.depth = 0;
        const char *res = match(&ms, s1, p);
        if (ms.error) {
            *error = true;
            return false;
        }
        if (res != nullptr) {
            // found: Lua then hands out the captures, and one not closed is an error
            for (int l = 0; l < ms.level; l++) {
                if (ms.capture[l].len == CAP_UNFINISHED) {
                    *error = true;
                    return false;
                }
            }
            return true;
        }
    } while (s1++ < ms.src_end && !anchor);
    return false;
}

// #endregion

namespace {

enum class Kind { EQ_STRING, EQ_CAPTURE, ANY_OF, LUA_MATCH, CONTAINS, HAS_ANCESTOR, HAS_PARENT, KIND_EQ };

struct Filter {
    Kind kind;
    uint32_t capture;
    // EQ_CAPTURE: the other capture
    uint32_t other = 0;
    // eq? (not not-eq?), any-of? (not not-any-of?), lua-match? (not not-lua-match?)
    bool positive = true;
    // any-eq?, any-lua-match?, ...: one node is enough
    bool any = false;
    // EQ_STRING, ANY_OF, CONTAINS: the strings, in UTF-16
    std::vector<std::u16string> values;
    // HAS_ANCESTOR, HAS_PARENT, KIND_EQ: node types; LUA_MATCH: the pattern (or none)
    std::vector<std::string> names;
    bool has_pattern = false;
};

struct PatternFilters {
    std::vector<Filter> filters;
    // whether every predicate of the pattern is decided here (the others hold)
    bool complete = true;
};

using QueryFilters = std::vector<PatternFilters>;

struct CursorState {
    const TSQuery *query = nullptr;
    uint32_t start_byte = 0;
    std::u16string text;
    bool has_text = false;
};

std::mutex mutex;
std::unordered_map<const TSQuery *, std::shared_ptr<QueryFilters>> queries;
std::unordered_map<const TSQueryCursor *, CursorState> cursors;

std::u16string utf16(const char *s, uint32_t length) {
    std::u16string out;
    out.reserve(length);
    uint32_t i = 0;
    while (i < length) {
        uint32_t c = static_cast<unsigned char>(s[i]);
        int extra = c >= 0xF0 ? 3 : c >= 0xE0 ? 2 : c >= 0xC0 ? 1 : 0;
        if (extra > 0) c &= (0x3F >> extra);
        i++;
        for (int k = 0; k < extra && i < length; k++, i++) {
            c = (c << 6) | (static_cast<unsigned char>(s[i]) & 0x3F);
        }
        if (c >= 0x10000) {
            c -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (c >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (c & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(c));
        }
    }
    return out;
}

void append_utf8(std::string &out, const char16_t *s, size_t count) {
    for (size_t i = 0; i < count; i++) {
        uint32_t c = s[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < count && s[i + 1] >= 0xDC00 && s[i + 1] <= 0xDFFF) {
            c = 0x10000 + ((c - 0xD800) << 10) + (s[i + 1] - 0xDC00);
            i++;
        }
        if (c < 0x80) {
            out.push_back((char)c);
        } else if (c < 0x800) {
            out.push_back((char)(0xC0 | (c >> 6)));
            out.push_back((char)(0x80 | (c & 0x3F)));
        } else if (c < 0x10000) {
            out.push_back((char)(0xE0 | (c >> 12)));
            out.push_back((char)(0x80 | ((c >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (c & 0x3F)));
        } else {
            out.push_back((char)(0xF0 | (c >> 18)));
            out.push_back((char)(0x80 | ((c >> 12) & 0x3F)));
            out.push_back((char)(0x80 | ((c >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (c & 0x3F)));
        }
    }
}

std::string string_of(const TSQuery *query, uint32_t id) {
    uint32_t length;
    const char *chars = ts_query_string_value_for_id(query, id, &length);
    return std::string(chars, length);
}

bool starts_with(const std::string &s, const char *prefix) { return s.rfind(prefix, 0) == 0; }

// A predicate as the Kotlin side reads it (TSQuery, QueryPredicates): what it is
// decided with here, or false if it is not decided here (the pattern is then not
// complete). Those Kotlin holds, without a filter.
bool compile(const TSQuery *query, const TSQueryPredicateStep *steps, uint32_t argc, PatternFilters &out) {
    if (argc == 0 || steps[0].type != TSQueryPredicateStepTypeString) return true;
    std::string name = string_of(query, steps[0].value_id);
    const TSQueryPredicateStep *args = steps + 1;
    uint32_t count = argc - 1;
    bool first_is_capture = count >= 1 && args[0].type == TSQueryPredicateStepTypeCapture;

    // TSQuery's own: eq? and its forms, match? (a Vim regex), any-of?
    if (name == "eq?" || name == "not-eq?" || name == "any-eq?" || name == "any-not-eq?") {
        if (count != 2 || !first_is_capture) return false;
        Filter filter{};
        filter.capture = args[0].value_id;
        filter.positive = (name == "eq?" || name == "any-eq?");
        filter.any = starts_with(name, "any-");
        if (args[1].type == TSQueryPredicateStepTypeCapture) {
            filter.kind = Kind::EQ_CAPTURE;
            filter.other = args[1].value_id;
        } else {
            filter.kind = Kind::EQ_STRING;
            std::string value = string_of(query, args[1].value_id);
            filter.values.push_back(utf16(value.data(), (uint32_t)value.size()));
        }
        out.filters.push_back(std::move(filter));
        return true;
    }
    if (name == "match?" || name == "not-match?" || name == "any-match?" || name == "any-not-match?") return false;
    if (name == "any-of?" || name == "not-any-of?") {
        if (count < 1 || !first_is_capture) return false;
        Filter filter{};
        filter.kind = Kind::ANY_OF;
        filter.capture = args[0].value_id;
        filter.positive = (name == "any-of?");
        for (uint32_t k = 1; k < count; k++) {
            if (args[k].type != TSQueryPredicateStepTypeString) return false;
            std::string value = string_of(query, args[k].value_id);
            filter.values.push_back(utf16(value.data(), (uint32_t)value.size()));
        }
        out.filters.push_back(std::move(filter));
        return true;
    }

    // the others: QueryPredicates (directives and unknown ones hold)
    if (!name.empty() && name.back() == '!') return true;
    if (!first_is_capture) return true;
    bool any = starts_with(name, "any-");
    std::string base = any ? name.substr(4) : name;
    bool negated = starts_with(base, "not-");
    std::string kind = negated ? base.substr(4) : base;
    Filter filter{};
    filter.capture = args[0].value_id;
    filter.positive = !negated;
    filter.any = any;
    std::vector<std::string> literals;
    for (uint32_t k = 1; k < count; k++) {
        if (args[k].type == TSQueryPredicateStepTypeString) literals.push_back(string_of(query, args[k].value_id));
    }
    if (kind == "lua-match?") {
        filter.kind = Kind::LUA_MATCH;
        if (!literals.empty()) {
            filter.has_pattern = true;
            filter.names.push_back(literals[0]);
        }
    } else if (kind == "contains?") {
        filter.kind = Kind::CONTAINS;
        for (const auto &literal : literals) filter.values.push_back(utf16(literal.data(), (uint32_t)literal.size()));
    } else if (kind == "has-ancestor?") {
        filter.kind = Kind::HAS_ANCESTOR;
        filter.names = literals;
    } else if (kind == "has-parent?") {
        filter.kind = Kind::HAS_PARENT;
        filter.names = literals;
    } else if (kind == "kind-eq?") {
        filter.kind = Kind::KIND_EQ;
        filter.names = literals;
    } else if (kind == "vim-match?") {
        return false;
    } else {
        return true;
    }
    out.filters.push_back(std::move(filter));
    return true;
}

std::shared_ptr<QueryFilters> build(const TSQuery *query) {
    auto filters = std::make_shared<QueryFilters>();
    uint32_t pattern_count = ts_query_pattern_count(query);
    filters->resize(pattern_count);
    for (uint32_t pattern = 0; pattern < pattern_count; pattern++) {
        uint32_t step_count;
        const TSQueryPredicateStep *steps = ts_query_predicates_for_pattern(query, pattern, &step_count);
        uint32_t start = 0;
        while (start < step_count) {
            uint32_t end = start;
            while (end < step_count && steps[end].type != TSQueryPredicateStepTypeDone) end++;
            if (!compile(query, steps + start, end - start, (*filters)[pattern])) (*filters)[pattern].complete = false;
            start = end + 1;
        }
    }
    return filters;
}

enum class Truth { FALSE, TRUE, UNKNOWN };

// the text of [node] in the text given, or false if it is not all in it
bool text_of(const TSNode &node, const CursorState &state, const char16_t **text, size_t *count) {
    uint32_t start = ts_node_start_byte(node), end = ts_node_end_byte(node);
    uint64_t window_end = state.start_byte + 2ull * state.text.size();
    if (start < state.start_byte || end > window_end || ((start - state.start_byte) & 1) != 0 || end < start) {
        return false;
    }
    *text = state.text.data() + (start - state.start_byte) / 2;
    *count = (end - start) / 2;
    return true;
}

bool equals(const char16_t *text, size_t count, const std::u16string &value) {
    return value.size() == count && std::char_traits<char16_t>::compare(text, value.data(), count) == 0;
}

bool contains(const char16_t *text, size_t count, const std::u16string &value) {
    if (value.empty()) return true;
    if (value.size() > count) return false;
    for (size_t i = 0; i + value.size() <= count; i++) {
        if (std::char_traits<char16_t>::compare(text + i, value.data(), value.size()) == 0) return true;
    }
    return false;
}

bool type_in(const TSNode &node, const std::vector<std::string> &names) {
    const char *type = ts_node_type(node);
    for (const auto &name : names) {
        if (name == type) return true;
    }
    return false;
}

// The ancestors of nodes, from the root down, found once per node and match: what the
// parents are, without asking for each parent in turn. (ts_node_parent descends from the
// root itself, once for every parent: under the root of a large file, with thousands of
// children, that is most of the time of has-ancestor?.) The same nodes as ts_node_parent,
// found the same way.
struct Ancestors {
    struct Path {
        const void *id;
        std::vector<TSNode> nodes;
    };
    Path paths[4];
    int count = 0;

    const std::vector<TSNode> &of(const TSNode &node) {
        for (int i = 0; i < count; i++) {
            if (paths[i].id == node.id) return paths[i].nodes;
        }
        Path &path = paths[count < 4 ? count++ : 3];
        path.id = node.id;
        path.nodes.clear();
        TSNode current = ts_tree_root_node(node.tree);
        if (current.id == node.id) return path.nodes;
        while (true) {
            TSNode next = ts_node_child_with_descendant(current, node);
            if (ts_node_is_null(next)) break;
            path.nodes.push_back(current);
            if (next.id == node.id) break;
            current = next;
        }
        return path.nodes;
    }
};

// One node's test for the predicates of QueryPredicates (before the not- is applied).
Truth node_test(const Filter &filter, const TSNode &node, const CursorState &state, Ancestors &ancestors) {
    switch (filter.kind) {
        case Kind::LUA_MATCH: {
            // no pattern: Kotlin matches nothing with it
            if (!filter.has_pattern) return Truth::FALSE;
            const char16_t *text;
            size_t count;
            if (!text_of(node, state, &text, &count)) return Truth::UNKNOWN;
            std::string bytes;
            bytes.reserve(count);
            append_utf8(bytes, text, count);
            bool error = false;
            bool found = lua_find(bytes.c_str(), bytes.size(), filter.names[0].c_str(), &error);
            // a pattern Lua would not take: as Kotlin has it
            if (error) return Truth::UNKNOWN;
            return found ? Truth::TRUE : Truth::FALSE;
        }
        case Kind::CONTAINS: {
            const char16_t *text;
            size_t count;
            if (!text_of(node, state, &text, &count)) return Truth::UNKNOWN;
            for (const auto &value : filter.values) {
                if (contains(text, count, value)) return Truth::TRUE;
            }
            return Truth::FALSE;
        }
        case Kind::HAS_ANCESTOR: {
            for (const TSNode &ancestor : ancestors.of(node)) {
                if (type_in(ancestor, filter.names)) return Truth::TRUE;
            }
            return Truth::FALSE;
        }
        case Kind::HAS_PARENT: {
            const std::vector<TSNode> &path = ancestors.of(node);
            return (!path.empty() && type_in(path.back(), filter.names)) ? Truth::TRUE : Truth::FALSE;
        }
        case Kind::KIND_EQ:
            return type_in(node, filter.names) ? Truth::TRUE : Truth::FALSE;
        default:
            return Truth::UNKNOWN;
    }
}

// what [filter] says of [match]
Truth evaluate(const Filter &filter, const TSQueryMatch &match, const CursorState &state, Ancestors &ancestors) {
    const TSNode *nodes[64];
    size_t node_count = 0;
    for (uint16_t k = 0; k < match.capture_count; k++) {
        if (match.captures[k].index != filter.capture) continue;
        if (node_count == 64) return Truth::UNKNOWN;
        nodes[node_count++] = &match.captures[k].node;
    }
    bool unknown = false;
    switch (filter.kind) {
        case Kind::EQ_STRING: {
            // TSQueryPredicate.EqString
            if (node_count == 0) return filter.positive ? Truth::FALSE : Truth::TRUE;
            for (size_t i = 0; i < node_count; i++) {
                const char16_t *text;
                size_t count;
                if (!text_of(*nodes[i], state, &text, &count)) { unknown = true; continue; }
                bool ok = equals(text, count, filter.values[0]) == filter.positive;
                if (filter.any && ok) return Truth::TRUE;
                if (!filter.any && !ok) return Truth::FALSE;
            }
            if (unknown) return Truth::UNKNOWN;
            return filter.any ? Truth::FALSE : Truth::TRUE;
        }
        case Kind::EQ_CAPTURE: {
            // TSQueryPredicate.EqCapture: all (or any) of the first, each against any of the other
            const TSNode *others[64];
            size_t other_count = 0;
            for (uint16_t k = 0; k < match.capture_count; k++) {
                if (match.captures[k].index != filter.other) continue;
                if (other_count == 64) return Truth::UNKNOWN;
                others[other_count++] = &match.captures[k].node;
            }
            for (size_t i = 0; i < node_count; i++) {
                const char16_t *text;
                size_t count;
                if (!text_of(*nodes[i], state, &text, &count)) { unknown = true; continue; }
                bool ok = false, open = false;
                for (size_t j = 0; j < other_count && !ok; j++) {
                    const char16_t *other;
                    size_t other_length;
                    if (!text_of(*others[j], state, &other, &other_length)) { open = true; continue; }
                    bool same = other_length == count && std::char_traits<char16_t>::compare(text, other, count) == 0;
                    if (same == filter.positive) ok = true;
                }
                if (!ok && open) { unknown = true; continue; }
                if (filter.any && ok) return Truth::TRUE;
                if (!filter.any && !ok) return Truth::FALSE;
            }
            if (unknown) return Truth::UNKNOWN;
            return filter.any ? Truth::FALSE : Truth::TRUE;
        }
        case Kind::ANY_OF: {
            // TSQueryPredicate.AnyOf: every node in (or, negated, out of) the strings
            for (size_t i = 0; i < node_count; i++) {
                const char16_t *text;
                size_t count;
                if (!text_of(*nodes[i], state, &text, &count)) { unknown = true; continue; }
                bool in = false;
                for (const auto &value : filter.values) {
                    if (equals(text, count, value)) { in = true; break; }
                }
                if (in != filter.positive) return Truth::FALSE;
            }
            return unknown ? Truth::UNKNOWN : Truth::TRUE;
        }
        default: {
            // QueryPredicates: no node holds; any-: one node, otherwise every node
            if (node_count == 0) return Truth::TRUE;
            for (size_t i = 0; i < node_count; i++) {
                Truth t = node_test(filter, *nodes[i], state, ancestors);
                if (t == Truth::UNKNOWN) { unknown = true; continue; }
                bool ok = (t == Truth::TRUE) == filter.positive;
                if (filter.any && ok) return Truth::TRUE;
                if (!filter.any && !ok) return Truth::FALSE;
            }
            if (unknown) return Truth::UNKNOWN;
            return filter.any ? Truth::FALSE : Truth::TRUE;
        }
    }
}

} // namespace

void set_query(const TSQueryCursor *cursor, const TSQuery *query) {
    std::lock_guard<std::mutex> lock(mutex);
    cursors[cursor].query = query;
}

void set_text(const TSQueryCursor *cursor, uint32_t start_byte, std::u16string &&text) {
    std::lock_guard<std::mutex> lock(mutex);
    CursorState &state = cursors[cursor];
    state.start_byte = start_byte;
    state.text = std::move(text);
    state.has_text = true;
}

void forget_cursor(const TSQueryCursor *cursor) {
    std::lock_guard<std::mutex> lock(mutex);
    cursors.erase(cursor);
}

void forget_query(const TSQuery *query) {
    std::lock_guard<std::mutex> lock(mutex);
    queries.erase(query);
}

Decision decide(const TSQueryCursor *cursor, const TSQueryMatch &match) {
    std::shared_ptr<QueryFilters> filters;
    const CursorState *state;
    {
        std::lock_guard<std::mutex> lock(mutex);
        auto found_state = cursors.find(cursor);
        if (found_state == cursors.end() || !found_state->second.has_text || found_state->second.query == nullptr) {
            return Decision::UNDECIDED;
        }
        const TSQuery *query = found_state->second.query;
        auto found = queries.find(query);
        if (found == queries.end()) found = queries.emplace(query, build(query)).first;
        filters = found->second;
        // the state of a cursor is only changed by the thread that runs it
        state = &found_state->second;
    }
    if (match.pattern_index >= filters->size()) return Decision::UNDECIDED;
    const PatternFilters &pattern = (*filters)[match.pattern_index];
    bool decided = pattern.complete;
    Ancestors ancestors;
    for (const Filter &filter : pattern.filters) {
        Truth truth = evaluate(filter, match, *state, ancestors);
        if (truth == Truth::FALSE) return Decision::REJECT;
        if (truth == Truth::UNKNOWN) decided = false;
    }
    return decided ? Decision::ACCEPT : Decision::UNDECIDED;
}

} // namespace query_filter
