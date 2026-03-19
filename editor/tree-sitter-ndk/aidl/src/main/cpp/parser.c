#include <tree_sitter/parser.h>

#if defined(__GNUC__) || defined(__clang__)
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wmissing-field-initializers"
#endif

#define LANGUAGE_VERSION 14
#define STATE_COUNT 330
#define LARGE_STATE_COUNT 6
#define SYMBOL_COUNT 162
#define ALIAS_COUNT 1
#define TOKEN_COUNT 80
#define EXTERNAL_TOKEN_COUNT 0
#define FIELD_COUNT 21
#define MAX_ALIAS_SEQUENCE_LENGTH 6
#define PRODUCTION_ID_COUNT 44

enum {
  sym_identifier = 1,
  sym_decimal_integer_literal = 2,
  sym_hex_integer_literal = 3,
  sym_octal_integer_literal = 4,
  sym_binary_integer_literal = 5,
  sym_decimal_floating_point_literal = 6,
  sym_hex_floating_point_literal = 7,
  sym_true = 8,
  sym_false = 9,
  sym_character_literal = 10,
  anon_sym_DQUOTE = 11,
  sym_string_fragment = 12,
  sym_escape_sequence = 13,
  sym_null_literal = 14,
  anon_sym_LPAREN = 15,
  anon_sym_AMP = 16,
  anon_sym_RPAREN = 17,
  anon_sym_EQ = 18,
  anon_sym_GT = 19,
  anon_sym_LT = 20,
  anon_sym_GT_EQ = 21,
  anon_sym_LT_EQ = 22,
  anon_sym_EQ_EQ = 23,
  anon_sym_BANG_EQ = 24,
  anon_sym_AMP_AMP = 25,
  anon_sym_PIPE_PIPE = 26,
  anon_sym_PLUS = 27,
  anon_sym_DASH = 28,
  anon_sym_STAR = 29,
  anon_sym_SLASH = 30,
  anon_sym_PIPE = 31,
  anon_sym_CARET = 32,
  anon_sym_PERCENT = 33,
  anon_sym_LT_LT = 34,
  anon_sym_GT_GT = 35,
  anon_sym_GT_GT_GT = 36,
  anon_sym_LBRACK = 37,
  anon_sym_RBRACK = 38,
  anon_sym_DOT = 39,
  anon_sym_class = 40,
  anon_sym_COMMA = 41,
  anon_sym_QMARK = 42,
  anon_sym_extends = 43,
  anon_sym_SEMI = 44,
  anon_sym_LBRACE = 45,
  anon_sym_RBRACE = 46,
  anon_sym_assert = 47,
  anon_sym_COLON = 48,
  anon_sym_return = 49,
  anon_sym_yield = 50,
  anon_sym_AT = 51,
  anon_sym_package = 52,
  anon_sym_import = 53,
  anon_sym_static = 54,
  anon_sym_oneway = 55,
  anon_sym_in = 56,
  anon_sym_out = 57,
  anon_sym_inout = 58,
  anon_sym_default = 59,
  anon_sym_interface = 60,
  anon_sym_parcelable = 61,
  anon_sym_byte = 62,
  anon_sym_short = 63,
  anon_sym_int = 64,
  anon_sym_long = 65,
  anon_sym_char = 66,
  anon_sym_float = 67,
  anon_sym_double = 68,
  sym_boolean_type = 69,
  sym_void_type = 70,
  anon_sym_DOT_DOT_DOT = 71,
  anon_sym_throws = 72,
  anon_sym_open = 73,
  anon_sym_module = 74,
  anon_sym_record = 75,
  sym_this = 76,
  sym_super = 77,
  sym_line_comment = 78,
  sym_block_comment = 79,
  sym_program = 80,
  sym__literal = 81,
  sym_string_literal = 82,
  sym_expression = 83,
  sym_cast_expression = 84,
  sym_assignment_expression = 85,
  sym_binary_expression = 86,
  sym_primary_expression = 87,
  sym_parenthesized_expression = 88,
  sym_class_literal = 89,
  sym_field_access = 90,
  sym_array_access = 91,
  sym_type_arguments = 92,
  sym_wildcard = 93,
  sym__wildcard_bounds = 94,
  sym_dimensions = 95,
  sym_statement = 96,
  sym_block = 97,
  sym_expression_statement = 98,
  sym_assert_statement = 99,
  sym_return_statement = 100,
  sym_yield_statement = 101,
  sym__annotation = 102,
  sym_marker_annotation = 103,
  sym_annotation = 104,
  sym_annotation_argument_list = 105,
  sym_element_value_pair = 106,
  sym__element_value = 107,
  sym_element_value_array_initializer = 108,
  sym_declaration = 109,
  sym_package_declaration = 110,
  sym_import_declaration = 111,
  sym_single_type_import = 112,
  sym_type_import_on_demand = 113,
  sym_single_static_import = 114,
  sym_static_import_on_demand = 115,
  sym_asterisk = 116,
  sym_modifiers = 117,
  sym_type_parameters = 118,
  sym_type_parameter = 119,
  sym_type_bound = 120,
  sym_scoped_identifier = 121,
  sym_field_declaration = 122,
  sym_interface_declaration = 123,
  sym_interface_body = 124,
  sym__variable_declarator_list = 125,
  sym_variable_declarator = 126,
  sym_parcelable_declaration = 127,
  sym_parcelable_body = 128,
  sym__type = 129,
  sym__unannotated_type = 130,
  sym_annotated_type = 131,
  sym_scoped_type_identifier = 132,
  sym_generic_type = 133,
  sym_array_type = 134,
  sym_integral_type = 135,
  sym_floating_point_type = 136,
  sym__method_header = 137,
  sym__method_declarator = 138,
  sym_formal_parameters = 139,
  sym_formal_parameter = 140,
  sym_receiver_parameter = 141,
  sym_spread_parameter = 142,
  sym_throws = 143,
  sym_local_variable_declaration = 144,
  sym_method_declaration = 145,
  aux_sym_program_repeat1 = 146,
  aux_sym_string_literal_repeat1 = 147,
  aux_sym_cast_expression_repeat1 = 148,
  aux_sym_dimensions_expr_repeat1 = 149,
  aux_sym_type_arguments_repeat1 = 150,
  aux_sym_dimensions_repeat1 = 151,
  aux_sym_annotation_argument_list_repeat1 = 152,
  aux_sym_element_value_array_initializer_repeat1 = 153,
  aux_sym_modifiers_repeat1 = 154,
  aux_sym_type_parameters_repeat1 = 155,
  aux_sym_type_bound_repeat1 = 156,
  aux_sym_type_list_repeat1 = 157,
  aux_sym_interface_body_repeat1 = 158,
  aux_sym__variable_declarator_list_repeat1 = 159,
  aux_sym_parcelable_body_repeat1 = 160,
  aux_sym_formal_parameters_repeat1 = 161,
  alias_sym_type_identifier = 162,
};

static const char * const ts_symbol_names[] = {
  [ts_builtin_sym_end] = "end",
  [sym_identifier] = "identifier",
  [sym_decimal_integer_literal] = "decimal_integer_literal",
  [sym_hex_integer_literal] = "hex_integer_literal",
  [sym_octal_integer_literal] = "octal_integer_literal",
  [sym_binary_integer_literal] = "binary_integer_literal",
  [sym_decimal_floating_point_literal] = "decimal_floating_point_literal",
  [sym_hex_floating_point_literal] = "hex_floating_point_literal",
  [sym_true] = "true",
  [sym_false] = "false",
  [sym_character_literal] = "character_literal",
  [anon_sym_DQUOTE] = "\"",
  [sym_string_fragment] = "string_fragment",
  [sym_escape_sequence] = "escape_sequence",
  [sym_null_literal] = "null_literal",
  [anon_sym_LPAREN] = "(",
  [anon_sym_AMP] = "&",
  [anon_sym_RPAREN] = ")",
  [anon_sym_EQ] = "=",
  [anon_sym_GT] = ">",
  [anon_sym_LT] = "<",
  [anon_sym_GT_EQ] = ">=",
  [anon_sym_LT_EQ] = "<=",
  [anon_sym_EQ_EQ] = "==",
  [anon_sym_BANG_EQ] = "!=",
  [anon_sym_AMP_AMP] = "&&",
  [anon_sym_PIPE_PIPE] = "||",
  [anon_sym_PLUS] = "+",
  [anon_sym_DASH] = "-",
  [anon_sym_STAR] = "*",
  [anon_sym_SLASH] = "/",
  [anon_sym_PIPE] = "|",
  [anon_sym_CARET] = "^",
  [anon_sym_PERCENT] = "%",
  [anon_sym_LT_LT] = "<<",
  [anon_sym_GT_GT] = ">>",
  [anon_sym_GT_GT_GT] = ">>>",
  [anon_sym_LBRACK] = "[",
  [anon_sym_RBRACK] = "]",
  [anon_sym_DOT] = ".",
  [anon_sym_class] = "class",
  [anon_sym_COMMA] = ",",
  [anon_sym_QMARK] = "\?",
  [anon_sym_extends] = "extends",
  [anon_sym_SEMI] = ";",
  [anon_sym_LBRACE] = "{",
  [anon_sym_RBRACE] = "}",
  [anon_sym_assert] = "assert",
  [anon_sym_COLON] = ":",
  [anon_sym_return] = "return",
  [anon_sym_yield] = "yield",
  [anon_sym_AT] = "@",
  [anon_sym_package] = "package",
  [anon_sym_import] = "import",
  [anon_sym_static] = "static",
  [anon_sym_oneway] = "oneway",
  [anon_sym_in] = "in",
  [anon_sym_out] = "out",
  [anon_sym_inout] = "inout",
  [anon_sym_default] = "default",
  [anon_sym_interface] = "interface",
  [anon_sym_parcelable] = "parcelable",
  [anon_sym_byte] = "byte",
  [anon_sym_short] = "short",
  [anon_sym_int] = "int",
  [anon_sym_long] = "long",
  [anon_sym_char] = "char",
  [anon_sym_float] = "float",
  [anon_sym_double] = "double",
  [sym_boolean_type] = "boolean_type",
  [sym_void_type] = "void_type",
  [anon_sym_DOT_DOT_DOT] = "...",
  [anon_sym_throws] = "throws",
  [anon_sym_open] = "identifier",
  [anon_sym_module] = "identifier",
  [anon_sym_record] = "identifier",
  [sym_this] = "this",
  [sym_super] = "super",
  [sym_line_comment] = "line_comment",
  [sym_block_comment] = "block_comment",
  [sym_program] = "program",
  [sym__literal] = "_literal",
  [sym_string_literal] = "string_literal",
  [sym_expression] = "expression",
  [sym_cast_expression] = "cast_expression",
  [sym_assignment_expression] = "assignment_expression",
  [sym_binary_expression] = "binary_expression",
  [sym_primary_expression] = "primary_expression",
  [sym_parenthesized_expression] = "parenthesized_expression",
  [sym_class_literal] = "class_literal",
  [sym_field_access] = "field_access",
  [sym_array_access] = "array_access",
  [sym_type_arguments] = "type_arguments",
  [sym_wildcard] = "wildcard",
  [sym__wildcard_bounds] = "_wildcard_bounds",
  [sym_dimensions] = "dimensions",
  [sym_statement] = "statement",
  [sym_block] = "block",
  [sym_expression_statement] = "expression_statement",
  [sym_assert_statement] = "assert_statement",
  [sym_return_statement] = "return_statement",
  [sym_yield_statement] = "yield_statement",
  [sym__annotation] = "_annotation",
  [sym_marker_annotation] = "marker_annotation",
  [sym_annotation] = "annotation",
  [sym_annotation_argument_list] = "annotation_argument_list",
  [sym_element_value_pair] = "element_value_pair",
  [sym__element_value] = "_element_value",
  [sym_element_value_array_initializer] = "element_value_array_initializer",
  [sym_declaration] = "declaration",
  [sym_package_declaration] = "package_declaration",
  [sym_import_declaration] = "import_declaration",
  [sym_single_type_import] = "single_type_import",
  [sym_type_import_on_demand] = "type_import_on_demand",
  [sym_single_static_import] = "single_static_import",
  [sym_static_import_on_demand] = "static_import_on_demand",
  [sym_asterisk] = "asterisk",
  [sym_modifiers] = "modifiers",
  [sym_type_parameters] = "type_parameters",
  [sym_type_parameter] = "type_parameter",
  [sym_type_bound] = "type_bound",
  [sym_scoped_identifier] = "scoped_identifier",
  [sym_field_declaration] = "field_declaration",
  [sym_interface_declaration] = "interface_declaration",
  [sym_interface_body] = "interface_body",
  [sym__variable_declarator_list] = "_variable_declarator_list",
  [sym_variable_declarator] = "variable_declarator",
  [sym_parcelable_declaration] = "parcelable_declaration",
  [sym_parcelable_body] = "parcelable_body",
  [sym__type] = "_type",
  [sym__unannotated_type] = "_unannotated_type",
  [sym_annotated_type] = "annotated_type",
  [sym_scoped_type_identifier] = "scoped_type_identifier",
  [sym_generic_type] = "generic_type",
  [sym_array_type] = "array_type",
  [sym_integral_type] = "integral_type",
  [sym_floating_point_type] = "floating_point_type",
  [sym__method_header] = "_method_header",
  [sym__method_declarator] = "_method_declarator",
  [sym_formal_parameters] = "formal_parameters",
  [sym_formal_parameter] = "formal_parameter",
  [sym_receiver_parameter] = "receiver_parameter",
  [sym_spread_parameter] = "spread_parameter",
  [sym_throws] = "throws",
  [sym_local_variable_declaration] = "local_variable_declaration",
  [sym_method_declaration] = "method_declaration",
  [aux_sym_program_repeat1] = "program_repeat1",
  [aux_sym_string_literal_repeat1] = "string_literal_repeat1",
  [aux_sym_cast_expression_repeat1] = "cast_expression_repeat1",
  [aux_sym_dimensions_expr_repeat1] = "dimensions_expr_repeat1",
  [aux_sym_type_arguments_repeat1] = "type_arguments_repeat1",
  [aux_sym_dimensions_repeat1] = "dimensions_repeat1",
  [aux_sym_annotation_argument_list_repeat1] = "annotation_argument_list_repeat1",
  [aux_sym_element_value_array_initializer_repeat1] = "element_value_array_initializer_repeat1",
  [aux_sym_modifiers_repeat1] = "modifiers_repeat1",
  [aux_sym_type_parameters_repeat1] = "type_parameters_repeat1",
  [aux_sym_type_bound_repeat1] = "type_bound_repeat1",
  [aux_sym_type_list_repeat1] = "type_list_repeat1",
  [aux_sym_interface_body_repeat1] = "interface_body_repeat1",
  [aux_sym__variable_declarator_list_repeat1] = "_variable_declarator_list_repeat1",
  [aux_sym_parcelable_body_repeat1] = "parcelable_body_repeat1",
  [aux_sym_formal_parameters_repeat1] = "formal_parameters_repeat1",
  [alias_sym_type_identifier] = "type_identifier",
};

static const TSSymbol ts_symbol_map[] = {
  [ts_builtin_sym_end] = ts_builtin_sym_end,
  [sym_identifier] = sym_identifier,
  [sym_decimal_integer_literal] = sym_decimal_integer_literal,
  [sym_hex_integer_literal] = sym_hex_integer_literal,
  [sym_octal_integer_literal] = sym_octal_integer_literal,
  [sym_binary_integer_literal] = sym_binary_integer_literal,
  [sym_decimal_floating_point_literal] = sym_decimal_floating_point_literal,
  [sym_hex_floating_point_literal] = sym_hex_floating_point_literal,
  [sym_true] = sym_true,
  [sym_false] = sym_false,
  [sym_character_literal] = sym_character_literal,
  [anon_sym_DQUOTE] = anon_sym_DQUOTE,
  [sym_string_fragment] = sym_string_fragment,
  [sym_escape_sequence] = sym_escape_sequence,
  [sym_null_literal] = sym_null_literal,
  [anon_sym_LPAREN] = anon_sym_LPAREN,
  [anon_sym_AMP] = anon_sym_AMP,
  [anon_sym_RPAREN] = anon_sym_RPAREN,
  [anon_sym_EQ] = anon_sym_EQ,
  [anon_sym_GT] = anon_sym_GT,
  [anon_sym_LT] = anon_sym_LT,
  [anon_sym_GT_EQ] = anon_sym_GT_EQ,
  [anon_sym_LT_EQ] = anon_sym_LT_EQ,
  [anon_sym_EQ_EQ] = anon_sym_EQ_EQ,
  [anon_sym_BANG_EQ] = anon_sym_BANG_EQ,
  [anon_sym_AMP_AMP] = anon_sym_AMP_AMP,
  [anon_sym_PIPE_PIPE] = anon_sym_PIPE_PIPE,
  [anon_sym_PLUS] = anon_sym_PLUS,
  [anon_sym_DASH] = anon_sym_DASH,
  [anon_sym_STAR] = anon_sym_STAR,
  [anon_sym_SLASH] = anon_sym_SLASH,
  [anon_sym_PIPE] = anon_sym_PIPE,
  [anon_sym_CARET] = anon_sym_CARET,
  [anon_sym_PERCENT] = anon_sym_PERCENT,
  [anon_sym_LT_LT] = anon_sym_LT_LT,
  [anon_sym_GT_GT] = anon_sym_GT_GT,
  [anon_sym_GT_GT_GT] = anon_sym_GT_GT_GT,
  [anon_sym_LBRACK] = anon_sym_LBRACK,
  [anon_sym_RBRACK] = anon_sym_RBRACK,
  [anon_sym_DOT] = anon_sym_DOT,
  [anon_sym_class] = anon_sym_class,
  [anon_sym_COMMA] = anon_sym_COMMA,
  [anon_sym_QMARK] = anon_sym_QMARK,
  [anon_sym_extends] = anon_sym_extends,
  [anon_sym_SEMI] = anon_sym_SEMI,
  [anon_sym_LBRACE] = anon_sym_LBRACE,
  [anon_sym_RBRACE] = anon_sym_RBRACE,
  [anon_sym_assert] = anon_sym_assert,
  [anon_sym_COLON] = anon_sym_COLON,
  [anon_sym_return] = anon_sym_return,
  [anon_sym_yield] = anon_sym_yield,
  [anon_sym_AT] = anon_sym_AT,
  [anon_sym_package] = anon_sym_package,
  [anon_sym_import] = anon_sym_import,
  [anon_sym_static] = anon_sym_static,
  [anon_sym_oneway] = anon_sym_oneway,
  [anon_sym_in] = anon_sym_in,
  [anon_sym_out] = anon_sym_out,
  [anon_sym_inout] = anon_sym_inout,
  [anon_sym_default] = anon_sym_default,
  [anon_sym_interface] = anon_sym_interface,
  [anon_sym_parcelable] = anon_sym_parcelable,
  [anon_sym_byte] = anon_sym_byte,
  [anon_sym_short] = anon_sym_short,
  [anon_sym_int] = anon_sym_int,
  [anon_sym_long] = anon_sym_long,
  [anon_sym_char] = anon_sym_char,
  [anon_sym_float] = anon_sym_float,
  [anon_sym_double] = anon_sym_double,
  [sym_boolean_type] = sym_boolean_type,
  [sym_void_type] = sym_void_type,
  [anon_sym_DOT_DOT_DOT] = anon_sym_DOT_DOT_DOT,
  [anon_sym_throws] = anon_sym_throws,
  [anon_sym_open] = sym_identifier,
  [anon_sym_module] = sym_identifier,
  [anon_sym_record] = sym_identifier,
  [sym_this] = sym_this,
  [sym_super] = sym_super,
  [sym_line_comment] = sym_line_comment,
  [sym_block_comment] = sym_block_comment,
  [sym_program] = sym_program,
  [sym__literal] = sym__literal,
  [sym_string_literal] = sym_string_literal,
  [sym_expression] = sym_expression,
  [sym_cast_expression] = sym_cast_expression,
  [sym_assignment_expression] = sym_assignment_expression,
  [sym_binary_expression] = sym_binary_expression,
  [sym_primary_expression] = sym_primary_expression,
  [sym_parenthesized_expression] = sym_parenthesized_expression,
  [sym_class_literal] = sym_class_literal,
  [sym_field_access] = sym_field_access,
  [sym_array_access] = sym_array_access,
  [sym_type_arguments] = sym_type_arguments,
  [sym_wildcard] = sym_wildcard,
  [sym__wildcard_bounds] = sym__wildcard_bounds,
  [sym_dimensions] = sym_dimensions,
  [sym_statement] = sym_statement,
  [sym_block] = sym_block,
  [sym_expression_statement] = sym_expression_statement,
  [sym_assert_statement] = sym_assert_statement,
  [sym_return_statement] = sym_return_statement,
  [sym_yield_statement] = sym_yield_statement,
  [sym__annotation] = sym__annotation,
  [sym_marker_annotation] = sym_marker_annotation,
  [sym_annotation] = sym_annotation,
  [sym_annotation_argument_list] = sym_annotation_argument_list,
  [sym_element_value_pair] = sym_element_value_pair,
  [sym__element_value] = sym__element_value,
  [sym_element_value_array_initializer] = sym_element_value_array_initializer,
  [sym_declaration] = sym_declaration,
  [sym_package_declaration] = sym_package_declaration,
  [sym_import_declaration] = sym_import_declaration,
  [sym_single_type_import] = sym_single_type_import,
  [sym_type_import_on_demand] = sym_type_import_on_demand,
  [sym_single_static_import] = sym_single_static_import,
  [sym_static_import_on_demand] = sym_static_import_on_demand,
  [sym_asterisk] = sym_asterisk,
  [sym_modifiers] = sym_modifiers,
  [sym_type_parameters] = sym_type_parameters,
  [sym_type_parameter] = sym_type_parameter,
  [sym_type_bound] = sym_type_bound,
  [sym_scoped_identifier] = sym_scoped_identifier,
  [sym_field_declaration] = sym_field_declaration,
  [sym_interface_declaration] = sym_interface_declaration,
  [sym_interface_body] = sym_interface_body,
  [sym__variable_declarator_list] = sym__variable_declarator_list,
  [sym_variable_declarator] = sym_variable_declarator,
  [sym_parcelable_declaration] = sym_parcelable_declaration,
  [sym_parcelable_body] = sym_parcelable_body,
  [sym__type] = sym__type,
  [sym__unannotated_type] = sym__unannotated_type,
  [sym_annotated_type] = sym_annotated_type,
  [sym_scoped_type_identifier] = sym_scoped_type_identifier,
  [sym_generic_type] = sym_generic_type,
  [sym_array_type] = sym_array_type,
  [sym_integral_type] = sym_integral_type,
  [sym_floating_point_type] = sym_floating_point_type,
  [sym__method_header] = sym__method_header,
  [sym__method_declarator] = sym__method_declarator,
  [sym_formal_parameters] = sym_formal_parameters,
  [sym_formal_parameter] = sym_formal_parameter,
  [sym_receiver_parameter] = sym_receiver_parameter,
  [sym_spread_parameter] = sym_spread_parameter,
  [sym_throws] = sym_throws,
  [sym_local_variable_declaration] = sym_local_variable_declaration,
  [sym_method_declaration] = sym_method_declaration,
  [aux_sym_program_repeat1] = aux_sym_program_repeat1,
  [aux_sym_string_literal_repeat1] = aux_sym_string_literal_repeat1,
  [aux_sym_cast_expression_repeat1] = aux_sym_cast_expression_repeat1,
  [aux_sym_dimensions_expr_repeat1] = aux_sym_dimensions_expr_repeat1,
  [aux_sym_type_arguments_repeat1] = aux_sym_type_arguments_repeat1,
  [aux_sym_dimensions_repeat1] = aux_sym_dimensions_repeat1,
  [aux_sym_annotation_argument_list_repeat1] = aux_sym_annotation_argument_list_repeat1,
  [aux_sym_element_value_array_initializer_repeat1] = aux_sym_element_value_array_initializer_repeat1,
  [aux_sym_modifiers_repeat1] = aux_sym_modifiers_repeat1,
  [aux_sym_type_parameters_repeat1] = aux_sym_type_parameters_repeat1,
  [aux_sym_type_bound_repeat1] = aux_sym_type_bound_repeat1,
  [aux_sym_type_list_repeat1] = aux_sym_type_list_repeat1,
  [aux_sym_interface_body_repeat1] = aux_sym_interface_body_repeat1,
  [aux_sym__variable_declarator_list_repeat1] = aux_sym__variable_declarator_list_repeat1,
  [aux_sym_parcelable_body_repeat1] = aux_sym_parcelable_body_repeat1,
  [aux_sym_formal_parameters_repeat1] = aux_sym_formal_parameters_repeat1,
  [alias_sym_type_identifier] = alias_sym_type_identifier,
};

static const TSSymbolMetadata ts_symbol_metadata[] = {
  [ts_builtin_sym_end] = {
    .visible = false,
    .named = true,
  },
  [sym_identifier] = {
    .visible = true,
    .named = true,
  },
  [sym_decimal_integer_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_hex_integer_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_octal_integer_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_binary_integer_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_decimal_floating_point_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_hex_floating_point_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_true] = {
    .visible = true,
    .named = true,
  },
  [sym_false] = {
    .visible = true,
    .named = true,
  },
  [sym_character_literal] = {
    .visible = true,
    .named = true,
  },
  [anon_sym_DQUOTE] = {
    .visible = true,
    .named = false,
  },
  [sym_string_fragment] = {
    .visible = true,
    .named = true,
  },
  [sym_escape_sequence] = {
    .visible = true,
    .named = true,
  },
  [sym_null_literal] = {
    .visible = true,
    .named = true,
  },
  [anon_sym_LPAREN] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_AMP] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_RPAREN] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_EQ] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_GT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_LT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_GT_EQ] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_LT_EQ] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_EQ_EQ] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_BANG_EQ] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_AMP_AMP] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_PIPE_PIPE] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_PLUS] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_DASH] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_STAR] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_SLASH] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_PIPE] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_CARET] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_PERCENT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_LT_LT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_GT_GT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_GT_GT_GT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_LBRACK] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_RBRACK] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_DOT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_class] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_COMMA] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_QMARK] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_extends] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_SEMI] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_LBRACE] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_RBRACE] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_assert] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_COLON] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_return] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_yield] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_AT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_package] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_import] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_static] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_oneway] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_in] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_out] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_inout] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_default] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_interface] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_parcelable] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_byte] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_short] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_int] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_long] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_char] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_float] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_double] = {
    .visible = true,
    .named = false,
  },
  [sym_boolean_type] = {
    .visible = true,
    .named = true,
  },
  [sym_void_type] = {
    .visible = true,
    .named = true,
  },
  [anon_sym_DOT_DOT_DOT] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_throws] = {
    .visible = true,
    .named = false,
  },
  [anon_sym_open] = {
    .visible = true,
    .named = true,
  },
  [anon_sym_module] = {
    .visible = true,
    .named = true,
  },
  [anon_sym_record] = {
    .visible = true,
    .named = true,
  },
  [sym_this] = {
    .visible = true,
    .named = true,
  },
  [sym_super] = {
    .visible = true,
    .named = true,
  },
  [sym_line_comment] = {
    .visible = true,
    .named = true,
  },
  [sym_block_comment] = {
    .visible = true,
    .named = true,
  },
  [sym_program] = {
    .visible = true,
    .named = true,
  },
  [sym__literal] = {
    .visible = false,
    .named = true,
    .supertype = true,
  },
  [sym_string_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_expression] = {
    .visible = false,
    .named = true,
    .supertype = true,
  },
  [sym_cast_expression] = {
    .visible = true,
    .named = true,
  },
  [sym_assignment_expression] = {
    .visible = true,
    .named = true,
  },
  [sym_binary_expression] = {
    .visible = true,
    .named = true,
  },
  [sym_primary_expression] = {
    .visible = false,
    .named = true,
    .supertype = true,
  },
  [sym_parenthesized_expression] = {
    .visible = true,
    .named = true,
  },
  [sym_class_literal] = {
    .visible = true,
    .named = true,
  },
  [sym_field_access] = {
    .visible = true,
    .named = true,
  },
  [sym_array_access] = {
    .visible = true,
    .named = true,
  },
  [sym_type_arguments] = {
    .visible = true,
    .named = true,
  },
  [sym_wildcard] = {
    .visible = true,
    .named = true,
  },
  [sym__wildcard_bounds] = {
    .visible = false,
    .named = true,
  },
  [sym_dimensions] = {
    .visible = true,
    .named = true,
  },
  [sym_statement] = {
    .visible = false,
    .named = true,
    .supertype = true,
  },
  [sym_block] = {
    .visible = true,
    .named = true,
  },
  [sym_expression_statement] = {
    .visible = true,
    .named = true,
  },
  [sym_assert_statement] = {
    .visible = true,
    .named = true,
  },
  [sym_return_statement] = {
    .visible = true,
    .named = true,
  },
  [sym_yield_statement] = {
    .visible = true,
    .named = true,
  },
  [sym__annotation] = {
    .visible = false,
    .named = true,
  },
  [sym_marker_annotation] = {
    .visible = true,
    .named = true,
  },
  [sym_annotation] = {
    .visible = true,
    .named = true,
  },
  [sym_annotation_argument_list] = {
    .visible = true,
    .named = true,
  },
  [sym_element_value_pair] = {
    .visible = true,
    .named = true,
  },
  [sym__element_value] = {
    .visible = false,
    .named = true,
  },
  [sym_element_value_array_initializer] = {
    .visible = true,
    .named = true,
  },
  [sym_declaration] = {
    .visible = false,
    .named = true,
    .supertype = true,
  },
  [sym_package_declaration] = {
    .visible = true,
    .named = true,
  },
  [sym_import_declaration] = {
    .visible = true,
    .named = true,
  },
  [sym_single_type_import] = {
    .visible = true,
    .named = true,
  },
  [sym_type_import_on_demand] = {
    .visible = true,
    .named = true,
  },
  [sym_single_static_import] = {
    .visible = true,
    .named = true,
  },
  [sym_static_import_on_demand] = {
    .visible = true,
    .named = true,
  },
  [sym_asterisk] = {
    .visible = true,
    .named = true,
  },
  [sym_modifiers] = {
    .visible = true,
    .named = true,
  },
  [sym_type_parameters] = {
    .visible = true,
    .named = true,
  },
  [sym_type_parameter] = {
    .visible = true,
    .named = true,
  },
  [sym_type_bound] = {
    .visible = true,
    .named = true,
  },
  [sym_scoped_identifier] = {
    .visible = true,
    .named = true,
  },
  [sym_field_declaration] = {
    .visible = true,
    .named = true,
  },
  [sym_interface_declaration] = {
    .visible = true,
    .named = true,
  },
  [sym_interface_body] = {
    .visible = true,
    .named = true,
  },
  [sym__variable_declarator_list] = {
    .visible = false,
    .named = true,
  },
  [sym_variable_declarator] = {
    .visible = true,
    .named = true,
  },
  [sym_parcelable_declaration] = {
    .visible = true,
    .named = true,
  },
  [sym_parcelable_body] = {
    .visible = true,
    .named = true,
  },
  [sym__type] = {
    .visible = false,
    .named = true,
    .supertype = true,
  },
  [sym__unannotated_type] = {
    .visible = false,
    .named = true,
    .supertype = true,
  },
  [sym_annotated_type] = {
    .visible = true,
    .named = true,
  },
  [sym_scoped_type_identifier] = {
    .visible = true,
    .named = true,
  },
  [sym_generic_type] = {
    .visible = true,
    .named = true,
  },
  [sym_array_type] = {
    .visible = true,
    .named = true,
  },
  [sym_integral_type] = {
    .visible = true,
    .named = true,
  },
  [sym_floating_point_type] = {
    .visible = true,
    .named = true,
  },
  [sym__method_header] = {
    .visible = false,
    .named = true,
  },
  [sym__method_declarator] = {
    .visible = false,
    .named = true,
  },
  [sym_formal_parameters] = {
    .visible = true,
    .named = true,
  },
  [sym_formal_parameter] = {
    .visible = true,
    .named = true,
  },
  [sym_receiver_parameter] = {
    .visible = true,
    .named = true,
  },
  [sym_spread_parameter] = {
    .visible = true,
    .named = true,
  },
  [sym_throws] = {
    .visible = true,
    .named = true,
  },
  [sym_local_variable_declaration] = {
    .visible = true,
    .named = true,
  },
  [sym_method_declaration] = {
    .visible = true,
    .named = true,
  },
  [aux_sym_program_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_string_literal_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_cast_expression_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_dimensions_expr_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_type_arguments_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_dimensions_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_annotation_argument_list_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_element_value_array_initializer_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_modifiers_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_type_parameters_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_type_bound_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_type_list_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_interface_body_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym__variable_declarator_list_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_parcelable_body_repeat1] = {
    .visible = false,
    .named = false,
  },
  [aux_sym_formal_parameters_repeat1] = {
    .visible = false,
    .named = false,
  },
  [alias_sym_type_identifier] = {
    .visible = true,
    .named = true,
  },
};

enum {
  field_arguments = 1,
  field_array = 2,
  field_body = 3,
  field_declarator = 4,
  field_dimensions = 5,
  field_element = 6,
  field_field = 7,
  field_index = 8,
  field_key = 9,
  field_left = 10,
  field_member = 11,
  field_name = 12,
  field_object = 13,
  field_operator = 14,
  field_parameters = 15,
  field_right = 16,
  field_scope = 17,
  field_type = 18,
  field_type_name = 19,
  field_type_parameters = 20,
  field_value = 21,
};

static const char * const ts_field_names[] = {
  [0] = NULL,
  [field_arguments] = "arguments",
  [field_array] = "array",
  [field_body] = "body",
  [field_declarator] = "declarator",
  [field_dimensions] = "dimensions",
  [field_element] = "element",
  [field_field] = "field",
  [field_index] = "index",
  [field_key] = "key",
  [field_left] = "left",
  [field_member] = "member",
  [field_name] = "name",
  [field_object] = "object",
  [field_operator] = "operator",
  [field_parameters] = "parameters",
  [field_right] = "right",
  [field_scope] = "scope",
  [field_type] = "type",
  [field_type_name] = "type_name",
  [field_type_parameters] = "type_parameters",
  [field_value] = "value",
};

static const TSFieldMapSlice ts_field_map_slices[PRODUCTION_ID_COUNT] = {
  [2] = {.index = 0, .length = 1},
  [3] = {.index = 1, .length = 1},
  [4] = {.index = 2, .length = 2},
  [5] = {.index = 4, .length = 1},
  [6] = {.index = 5, .length = 2},
  [7] = {.index = 7, .length = 2},
  [8] = {.index = 9, .length = 2},
  [9] = {.index = 11, .length = 2},
  [11] = {.index = 13, .length = 3},
  [12] = {.index = 16, .length = 2},
  [13] = {.index = 18, .length = 2},
  [14] = {.index = 20, .length = 2},
  [16] = {.index = 22, .length = 1},
  [17] = {.index = 23, .length = 2},
  [18] = {.index = 25, .length = 2},
  [19] = {.index = 27, .length = 2},
  [20] = {.index = 29, .length = 3},
  [22] = {.index = 32, .length = 2},
  [23] = {.index = 34, .length = 2},
  [24] = {.index = 36, .length = 2},
  [25] = {.index = 38, .length = 1},
  [26] = {.index = 39, .length = 2},
  [28] = {.index = 41, .length = 1},
  [29] = {.index = 42, .length = 3},
  [31] = {.index = 45, .length = 4},
  [32] = {.index = 49, .length = 5},
  [33] = {.index = 54, .length = 2},
  [34] = {.index = 56, .length = 3},
  [35] = {.index = 59, .length = 2},
  [36] = {.index = 61, .length = 2},
  [37] = {.index = 63, .length = 1},
  [38] = {.index = 64, .length = 5},
  [39] = {.index = 69, .length = 5},
  [40] = {.index = 74, .length = 2},
  [41] = {.index = 76, .length = 5},
  [42] = {.index = 81, .length = 3},
  [43] = {.index = 84, .length = 1},
};

static const TSFieldMapEntry ts_field_map_entries[] = {
  [0] =
    {field_name, 1},
  [1] =
    {field_name, 0},
  [2] =
    {field_dimensions, 1},
    {field_element, 0},
  [4] =
    {field_declarator, 0},
  [5] =
    {field_arguments, 2},
    {field_name, 1},
  [7] =
    {field_body, 2},
    {field_name, 1},
  [9] =
    {field_left, 0},
    {field_right, 2},
  [11] =
    {field_field, 2},
    {field_object, 0},
  [13] =
    {field_left, 0},
    {field_operator, 1},
    {field_right, 2},
  [16] =
    {field_dimensions, 1},
    {field_name, 0},
  [18] =
    {field_declarator, 1, .inherited = true},
    {field_type, 0},
  [20] =
    {field_declarator, 0},
    {field_declarator, 1, .inherited = true},
  [22] =
    {field_type, 1},
  [23] =
    {field_type, 1},
    {field_value, 3},
  [25] =
    {field_type, 0, .inherited = true},
    {field_type, 1, .inherited = true},
  [27] =
    {field_name, 2},
    {field_scope, 0},
  [29] =
    {field_body, 3},
    {field_name, 1},
    {field_type_parameters, 2},
  [32] =
    {field_array, 0},
    {field_index, 2},
  [34] =
    {field_body, 3},
    {field_name, 2},
  [36] =
    {field_declarator, 2, .inherited = true},
    {field_type, 1},
  [38] =
    {field_declarator, 1},
  [39] =
    {field_declarator, 0, .inherited = true},
    {field_declarator, 1, .inherited = true},
  [41] =
    {field_name, 2},
  [42] =
    {field_type, 1},
    {field_type, 2, .inherited = true},
    {field_value, 4},
  [45] =
    {field_dimensions, 1, .inherited = true},
    {field_name, 1, .inherited = true},
    {field_parameters, 1, .inherited = true},
    {field_type, 0},
  [49] =
    {field_dimensions, 0, .inherited = true},
    {field_name, 0, .inherited = true},
    {field_parameters, 0, .inherited = true},
    {field_type, 0, .inherited = true},
    {field_type_parameters, 0, .inherited = true},
  [54] =
    {field_field, 4},
    {field_object, 0},
  [56] =
    {field_body, 4},
    {field_name, 2},
    {field_type_parameters, 3},
  [59] =
    {field_key, 0},
    {field_value, 2},
  [61] =
    {field_member, 4},
    {field_scope, 2},
  [63] =
    {field_type_name, 2},
  [64] =
    {field_dimensions, 1, .inherited = true},
    {field_name, 1, .inherited = true},
    {field_parameters, 1, .inherited = true},
    {field_type, 1, .inherited = true},
    {field_type_parameters, 1, .inherited = true},
  [69] =
    {field_dimensions, 2, .inherited = true},
    {field_name, 2, .inherited = true},
    {field_parameters, 2, .inherited = true},
    {field_type, 1},
    {field_type_parameters, 0},
  [74] =
    {field_name, 0},
    {field_parameters, 1},
  [76] =
    {field_dimensions, 3, .inherited = true},
    {field_name, 3, .inherited = true},
    {field_parameters, 3, .inherited = true},
    {field_type, 2},
    {field_type_parameters, 0},
  [81] =
    {field_dimensions, 2},
    {field_name, 0},
    {field_parameters, 1},
  [84] =
    {field_type, 0},
};

static const TSSymbol ts_alias_sequences[PRODUCTION_ID_COUNT][MAX_ALIAS_SEQUENCE_LENGTH] = {
  [0] = {0},
  [1] = {
    [0] = alias_sym_type_identifier,
  },
  [10] = {
    [0] = alias_sym_type_identifier,
    [2] = alias_sym_type_identifier,
  },
  [15] = {
    [2] = alias_sym_type_identifier,
  },
  [21] = {
    [0] = alias_sym_type_identifier,
    [3] = alias_sym_type_identifier,
  },
  [27] = {
    [3] = alias_sym_type_identifier,
  },
  [30] = {
    [1] = alias_sym_type_identifier,
  },
};

static const uint16_t ts_non_terminal_alias_map[] = {
  0,
};

static const TSStateId ts_primary_state_ids[STATE_COUNT] = {
  [0] = 0,
  [1] = 1,
  [2] = 2,
  [3] = 3,
  [4] = 4,
  [5] = 5,
  [6] = 6,
  [7] = 7,
  [8] = 7,
  [9] = 9,
  [10] = 10,
  [11] = 11,
  [12] = 12,
  [13] = 13,
  [14] = 14,
  [15] = 15,
  [16] = 16,
  [17] = 17,
  [18] = 18,
  [19] = 19,
  [20] = 20,
  [21] = 21,
  [22] = 22,
  [23] = 23,
  [24] = 24,
  [25] = 25,
  [26] = 26,
  [27] = 27,
  [28] = 28,
  [29] = 29,
  [30] = 30,
  [31] = 31,
  [32] = 32,
  [33] = 33,
  [34] = 34,
  [35] = 35,
  [36] = 36,
  [37] = 37,
  [38] = 38,
  [39] = 39,
  [40] = 40,
  [41] = 41,
  [42] = 42,
  [43] = 43,
  [44] = 44,
  [45] = 45,
  [46] = 46,
  [47] = 47,
  [48] = 48,
  [49] = 49,
  [50] = 50,
  [51] = 51,
  [52] = 52,
  [53] = 53,
  [54] = 54,
  [55] = 55,
  [56] = 56,
  [57] = 57,
  [58] = 58,
  [59] = 59,
  [60] = 60,
  [61] = 61,
  [62] = 62,
  [63] = 63,
  [64] = 64,
  [65] = 65,
  [66] = 66,
  [67] = 67,
  [68] = 68,
  [69] = 69,
  [70] = 70,
  [71] = 71,
  [72] = 72,
  [73] = 73,
  [74] = 74,
  [75] = 75,
  [76] = 76,
  [77] = 77,
  [78] = 78,
  [79] = 79,
  [80] = 80,
  [81] = 81,
  [82] = 82,
  [83] = 83,
  [84] = 84,
  [85] = 85,
  [86] = 86,
  [87] = 87,
  [88] = 88,
  [89] = 89,
  [90] = 90,
  [91] = 91,
  [92] = 92,
  [93] = 93,
  [94] = 94,
  [95] = 95,
  [96] = 96,
  [97] = 97,
  [98] = 62,
  [99] = 99,
  [100] = 100,
  [101] = 101,
  [102] = 102,
  [103] = 103,
  [104] = 104,
  [105] = 105,
  [106] = 106,
  [107] = 107,
  [108] = 108,
  [109] = 109,
  [110] = 110,
  [111] = 111,
  [112] = 112,
  [113] = 113,
  [114] = 114,
  [115] = 115,
  [116] = 116,
  [117] = 117,
  [118] = 118,
  [119] = 119,
  [120] = 120,
  [121] = 121,
  [122] = 122,
  [123] = 123,
  [124] = 124,
  [125] = 125,
  [126] = 126,
  [127] = 127,
  [128] = 128,
  [129] = 129,
  [130] = 130,
  [131] = 125,
  [132] = 102,
  [133] = 121,
  [134] = 101,
  [135] = 135,
  [136] = 136,
  [137] = 112,
  [138] = 138,
  [139] = 139,
  [140] = 116,
  [141] = 120,
  [142] = 113,
  [143] = 143,
  [144] = 144,
  [145] = 145,
  [146] = 146,
  [147] = 147,
  [148] = 148,
  [149] = 149,
  [150] = 150,
  [151] = 151,
  [152] = 152,
  [153] = 153,
  [154] = 154,
  [155] = 155,
  [156] = 156,
  [157] = 138,
  [158] = 158,
  [159] = 159,
  [160] = 160,
  [161] = 161,
  [162] = 162,
  [163] = 163,
  [164] = 164,
  [165] = 165,
  [166] = 166,
  [167] = 167,
  [168] = 168,
  [169] = 169,
  [170] = 170,
  [171] = 171,
  [172] = 172,
  [173] = 173,
  [174] = 174,
  [175] = 175,
  [176] = 176,
  [177] = 177,
  [178] = 178,
  [179] = 179,
  [180] = 180,
  [181] = 181,
  [182] = 182,
  [183] = 183,
  [184] = 184,
  [185] = 185,
  [186] = 186,
  [187] = 187,
  [188] = 188,
  [189] = 149,
  [190] = 190,
  [191] = 191,
  [192] = 168,
  [193] = 193,
  [194] = 145,
  [195] = 195,
  [196] = 196,
  [197] = 197,
  [198] = 198,
  [199] = 199,
  [200] = 200,
  [201] = 201,
  [202] = 202,
  [203] = 203,
  [204] = 204,
  [205] = 205,
  [206] = 206,
  [207] = 207,
  [208] = 208,
  [209] = 209,
  [210] = 209,
  [211] = 211,
  [212] = 212,
  [213] = 213,
  [214] = 214,
  [215] = 215,
  [216] = 216,
  [217] = 217,
  [218] = 218,
  [219] = 219,
  [220] = 220,
  [221] = 221,
  [222] = 222,
  [223] = 223,
  [224] = 224,
  [225] = 225,
  [226] = 226,
  [227] = 227,
  [228] = 228,
  [229] = 229,
  [230] = 230,
  [231] = 231,
  [232] = 232,
  [233] = 233,
  [234] = 234,
  [235] = 235,
  [236] = 236,
  [237] = 237,
  [238] = 238,
  [239] = 239,
  [240] = 240,
  [241] = 241,
  [242] = 242,
  [243] = 243,
  [244] = 244,
  [245] = 245,
  [246] = 246,
  [247] = 247,
  [248] = 248,
  [249] = 249,
  [250] = 250,
  [251] = 251,
  [252] = 234,
  [253] = 253,
  [254] = 238,
  [255] = 255,
  [256] = 256,
  [257] = 257,
  [258] = 258,
  [259] = 259,
  [260] = 260,
  [261] = 261,
  [262] = 262,
  [263] = 263,
  [264] = 264,
  [265] = 265,
  [266] = 266,
  [267] = 267,
  [268] = 268,
  [269] = 269,
  [270] = 270,
  [271] = 271,
  [272] = 272,
  [273] = 273,
  [274] = 274,
  [275] = 275,
  [276] = 276,
  [277] = 277,
  [278] = 278,
  [279] = 279,
  [280] = 280,
  [281] = 281,
  [282] = 282,
  [283] = 283,
  [284] = 284,
  [285] = 285,
  [286] = 286,
  [287] = 287,
  [288] = 288,
  [289] = 289,
  [290] = 290,
  [291] = 291,
  [292] = 292,
  [293] = 293,
  [294] = 294,
  [295] = 295,
  [296] = 296,
  [297] = 297,
  [298] = 298,
  [299] = 299,
  [300] = 300,
  [301] = 301,
  [302] = 302,
  [303] = 303,
  [304] = 304,
  [305] = 305,
  [306] = 306,
  [307] = 307,
  [308] = 308,
  [309] = 309,
  [310] = 310,
  [311] = 311,
  [312] = 312,
  [313] = 313,
  [314] = 314,
  [315] = 315,
  [316] = 316,
  [317] = 317,
  [318] = 318,
  [319] = 319,
  [320] = 320,
  [321] = 321,
  [322] = 322,
  [323] = 323,
  [324] = 324,
  [325] = 325,
  [326] = 312,
  [327] = 327,
  [328] = 300,
  [329] = 329,
};

static inline bool sym_identifier_character_set_1(int32_t c) {
  return (c < 6688
    ? (c < 2984
      ? (c < 2365
        ? (c < 1488
          ? (c < 886
            ? (c < 216
              ? (c < 181
                ? (c < 'a'
                  ? (c >= '$' && c <= '_')
                  : (c <= 'z' || c == 170))
                : (c <= 181 || (c < 192
                  ? c == 186
                  : c <= 214)))
              : (c <= 246 || (c < 748
                ? (c < 710
                  ? (c >= 248 && c <= 705)
                  : (c <= 721 || (c >= 736 && c <= 740)))
                : (c <= 748 || (c < 880
                  ? c == 750
                  : c <= 884)))))
            : (c <= 887 || (c < 931
              ? (c < 904
                ? (c < 895
                  ? (c >= 890 && c <= 893)
                  : (c <= 895 || c == 902))
                : (c <= 906 || (c < 910
                  ? c == 908
                  : c <= 929)))
              : (c <= 1013 || (c < 1329
                ? (c < 1162
                  ? (c >= 1015 && c <= 1153)
                  : c <= 1327)
                : (c <= 1366 || (c < 1376
                  ? c == 1369
                  : c <= 1416)))))))
          : (c <= 1514 || (c < 1994
            ? (c < 1774
              ? (c < 1649
                ? (c < 1568
                  ? (c >= 1519 && c <= 1522)
                  : (c <= 1610 || (c >= 1646 && c <= 1647)))
                : (c <= 1747 || (c < 1765
                  ? c == 1749
                  : c <= 1766)))
              : (c <= 1775 || (c < 1810
                ? (c < 1791
                  ? (c >= 1786 && c <= 1788)
                  : (c <= 1791 || c == 1808))
                : (c <= 1839 || (c < 1969
                  ? (c >= 1869 && c <= 1957)
                  : c <= 1969)))))
            : (c <= 2026 || (c < 2112
              ? (c < 2074
                ? (c < 2042
                  ? (c >= 2036 && c <= 2037)
                  : (c <= 2042 || (c >= 2048 && c <= 2069)))
                : (c <= 2074 || (c < 2088
                  ? c == 2084
                  : c <= 2088)))
              : (c <= 2136 || (c < 2185
                ? (c < 2160
                  ? (c >= 2144 && c <= 2154)
                  : c <= 2183)
                : (c <= 2190 || (c < 2308
                  ? (c >= 2208 && c <= 2249)
                  : c <= 2361)))))))))
        : (c <= 2365 || (c < 2703
          ? (c < 2544
            ? (c < 2474
              ? (c < 2437
                ? (c < 2392
                  ? c == 2384
                  : (c <= 2401 || (c >= 2417 && c <= 2432)))
                : (c <= 2444 || (c < 2451
                  ? (c >= 2447 && c <= 2448)
                  : c <= 2472)))
              : (c <= 2480 || (c < 2510
                ? (c < 2486
                  ? c == 2482
                  : (c <= 2489 || c == 2493))
                : (c <= 2510 || (c < 2527
                  ? (c >= 2524 && c <= 2525)
                  : c <= 2529)))))
            : (c <= 2545 || (c < 2613
              ? (c < 2579
                ? (c < 2565
                  ? c == 2556
                  : (c <= 2570 || (c >= 2575 && c <= 2576)))
                : (c <= 2600 || (c < 2610
                  ? (c >= 2602 && c <= 2608)
                  : c <= 2611)))
              : (c <= 2614 || (c < 2654
                ? (c < 2649
                  ? (c >= 2616 && c <= 2617)
                  : c <= 2652)
                : (c <= 2654 || (c < 2693
                  ? (c >= 2674 && c <= 2676)
                  : c <= 2701)))))))
          : (c <= 2705 || (c < 2869
            ? (c < 2784
              ? (c < 2741
                ? (c < 2730
                  ? (c >= 2707 && c <= 2728)
                  : (c <= 2736 || (c >= 2738 && c <= 2739)))
                : (c <= 2745 || (c < 2768
                  ? c == 2749
                  : c <= 2768)))
              : (c <= 2785 || (c < 2835
                ? (c < 2821
                  ? c == 2809
                  : (c <= 2828 || (c >= 2831 && c <= 2832)))
                : (c <= 2856 || (c < 2866
                  ? (c >= 2858 && c <= 2864)
                  : c <= 2867)))))
            : (c <= 2873 || (c < 2958
              ? (c < 2929
                ? (c < 2908
                  ? c == 2877
                  : (c <= 2909 || (c >= 2911 && c <= 2913)))
                : (c <= 2929 || (c < 2949
                  ? c == 2947
                  : c <= 2954)))
              : (c <= 2960 || (c < 2972
                ? (c < 2969
                  ? (c >= 2962 && c <= 2965)
                  : c <= 2970)
                : (c <= 2972 || (c < 2979
                  ? (c >= 2974 && c <= 2975)
                  : c <= 2980)))))))))))
      : (c <= 2986 || (c < 4176
        ? (c < 3423
          ? (c < 3218
            ? (c < 3133
              ? (c < 3086
                ? (c < 3024
                  ? (c >= 2990 && c <= 3001)
                  : (c <= 3024 || (c >= 3077 && c <= 3084)))
                : (c <= 3088 || (c < 3114
                  ? (c >= 3090 && c <= 3112)
                  : c <= 3129)))
              : (c <= 3133 || (c < 3200
                ? (c < 3165
                  ? (c >= 3160 && c <= 3162)
                  : (c <= 3165 || (c >= 3168 && c <= 3169)))
                : (c <= 3200 || (c < 3214
                  ? (c >= 3205 && c <= 3212)
                  : c <= 3216)))))
            : (c <= 3240 || (c < 3332
              ? (c < 3293
                ? (c < 3253
                  ? (c >= 3242 && c <= 3251)
                  : (c <= 3257 || c == 3261))
                : (c <= 3294 || (c < 3313
                  ? (c >= 3296 && c <= 3297)
                  : c <= 3314)))
              : (c <= 3340 || (c < 3389
                ? (c < 3346
                  ? (c >= 3342 && c <= 3344)
                  : c <= 3386)
                : (c <= 3389 || (c < 3412
                  ? c == 3406
                  : c <= 3414)))))))
          : (c <= 3425 || (c < 3749
            ? (c < 3585
              ? (c < 3507
                ? (c < 3461
                  ? (c >= 3450 && c <= 3455)
                  : (c <= 3478 || (c >= 3482 && c <= 3505)))
                : (c <= 3515 || (c < 3520
                  ? c == 3517
                  : c <= 3526)))
              : (c <= 3632 || (c < 3716
                ? (c < 3648
                  ? (c >= 3634 && c <= 3635)
                  : (c <= 3654 || (c >= 3713 && c <= 3714)))
                : (c <= 3716 || (c < 3724
                  ? (c >= 3718 && c <= 3722)
                  : c <= 3747)))))
            : (c <= 3749 || (c < 3840
              ? (c < 3776
                ? (c < 3762
                  ? (c >= 3751 && c <= 3760)
                  : (c <= 3763 || c == 3773))
                : (c <= 3780 || (c < 3804
                  ? c == 3782
                  : c <= 3807)))
              : (c <= 3840 || (c < 3976
                ? (c < 3913
                  ? (c >= 3904 && c <= 3911)
                  : c <= 3948)
                : (c <= 3980 || (c < 4159
                  ? (c >= 4096 && c <= 4138)
                  : c <= 4159)))))))))
        : (c <= 4181 || (c < 4992
          ? (c < 4696
            ? (c < 4256
              ? (c < 4206
                ? (c < 4193
                  ? (c >= 4186 && c <= 4189)
                  : (c <= 4193 || (c >= 4197 && c <= 4198)))
                : (c <= 4208 || (c < 4238
                  ? (c >= 4213 && c <= 4225)
                  : c <= 4238)))
              : (c <= 4293 || (c < 4348
                ? (c < 4301
                  ? c == 4295
                  : (c <= 4301 || (c >= 4304 && c <= 4346)))
                : (c <= 4680 || (c < 4688
                  ? (c >= 4682 && c <= 4685)
                  : c <= 4694)))))
            : (c <= 4696 || (c < 4800
              ? (c < 4752
                ? (c < 4704
                  ? (c >= 4698 && c <= 4701)
                  : (c <= 4744 || (c >= 4746 && c <= 4749)))
                : (c <= 4784 || (c < 4792
                  ? (c >= 4786 && c <= 4789)
                  : c <= 4798)))
              : (c <= 4800 || (c < 4824
                ? (c < 4808
                  ? (c >= 4802 && c <= 4805)
                  : c <= 4822)
                : (c <= 4880 || (c < 4888
                  ? (c >= 4882 && c <= 4885)
                  : c <= 4954)))))))
          : (c <= 5007 || (c < 6103
            ? (c < 5873
              ? (c < 5743
                ? (c < 5112
                  ? (c >= 5024 && c <= 5109)
                  : (c <= 5117 || (c >= 5121 && c <= 5740)))
                : (c <= 5759 || (c < 5792
                  ? (c >= 5761 && c <= 5786)
                  : c <= 5866)))
              : (c <= 5880 || (c < 5984
                ? (c < 5919
                  ? (c >= 5888 && c <= 5905)
                  : (c <= 5937 || (c >= 5952 && c <= 5969)))
                : (c <= 5996 || (c < 6016
                  ? (c >= 5998 && c <= 6000)
                  : c <= 6067)))))
            : (c <= 6103 || (c < 6400
              ? (c < 6279
                ? (c < 6176
                  ? c == 6108
                  : (c <= 6264 || (c >= 6272 && c <= 6276)))
                : (c <= 6312 || (c < 6320
                  ? c == 6314
                  : c <= 6389)))
              : (c <= 6430 || (c < 6528
                ? (c < 6512
                  ? (c >= 6480 && c <= 6509)
                  : c <= 6516)
                : (c <= 6571 || (c < 6656
                  ? (c >= 6576 && c <= 6601)
                  : c <= 6678)))))))))))))
    : (c <= 6740 || (c < 43261
      ? (c < 11264
        ? (c < 8064
          ? (c < 7406
            ? (c < 7168
              ? (c < 7043
                ? (c < 6917
                  ? c == 6823
                  : (c <= 6963 || (c >= 6981 && c <= 6988)))
                : (c <= 7072 || (c < 7098
                  ? (c >= 7086 && c <= 7087)
                  : c <= 7141)))
              : (c <= 7203 || (c < 7312
                ? (c < 7258
                  ? (c >= 7245 && c <= 7247)
                  : (c <= 7293 || (c >= 7296 && c <= 7304)))
                : (c <= 7354 || (c < 7401
                  ? (c >= 7357 && c <= 7359)
                  : c <= 7404)))))
            : (c <= 7411 || (c < 8008
              ? (c < 7680
                ? (c < 7418
                  ? (c >= 7413 && c <= 7414)
                  : (c <= 7418 || (c >= 7424 && c <= 7615)))
                : (c <= 7957 || (c < 7968
                  ? (c >= 7960 && c <= 7965)
                  : c <= 8005)))
              : (c <= 8013 || (c < 8027
                ? (c < 8025
                  ? (c >= 8016 && c <= 8023)
                  : c <= 8025)
                : (c <= 8027 || (c < 8031
                  ? c == 8029
                  : c <= 8061)))))))
          : (c <= 8116 || (c < 8455
            ? (c < 8160
              ? (c < 8134
                ? (c < 8126
                  ? (c >= 8118 && c <= 8124)
                  : (c <= 8126 || (c >= 8130 && c <= 8132)))
                : (c <= 8140 || (c < 8150
                  ? (c >= 8144 && c <= 8147)
                  : c <= 8155)))
              : (c <= 8172 || (c < 8319
                ? (c < 8182
                  ? (c >= 8178 && c <= 8180)
                  : (c <= 8188 || c == 8305))
                : (c <= 8319 || (c < 8450
                  ? (c >= 8336 && c <= 8348)
                  : c <= 8450)))))
            : (c <= 8455 || (c < 8490
              ? (c < 8484
                ? (c < 8469
                  ? (c >= 8458 && c <= 8467)
                  : (c <= 8469 || (c >= 8473 && c <= 8477)))
                : (c <= 8484 || (c < 8488
                  ? c == 8486
                  : c <= 8488)))
              : (c <= 8493 || (c < 8517
                ? (c < 8508
                  ? (c >= 8495 && c <= 8505)
                  : c <= 8511)
                : (c <= 8521 || (c < 8579
                  ? c == 8526
                  : c <= 8580)))))))))
        : (c <= 11492 || (c < 12704
          ? (c < 11720
            ? (c < 11631
              ? (c < 11559
                ? (c < 11506
                  ? (c >= 11499 && c <= 11502)
                  : (c <= 11507 || (c >= 11520 && c <= 11557)))
                : (c <= 11559 || (c < 11568
                  ? c == 11565
                  : c <= 11623)))
              : (c <= 11631 || (c < 11696
                ? (c < 11680
                  ? (c >= 11648 && c <= 11670)
                  : (c <= 11686 || (c >= 11688 && c <= 11694)))
                : (c <= 11702 || (c < 11712
                  ? (c >= 11704 && c <= 11710)
                  : c <= 11718)))))
            : (c <= 11726 || (c < 12353
              ? (c < 12293
                ? (c < 11736
                  ? (c >= 11728 && c <= 11734)
                  : (c <= 11742 || c == 11823))
                : (c <= 12294 || (c < 12347
                  ? (c >= 12337 && c <= 12341)
                  : c <= 12348)))
              : (c <= 12438 || (c < 12540
                ? (c < 12449
                  ? (c >= 12445 && c <= 12447)
                  : c <= 12538)
                : (c <= 12543 || (c < 12593
                  ? (c >= 12549 && c <= 12591)
                  : c <= 12686)))))))
          : (c <= 12735 || (c < 42786
            ? (c < 42240
              ? (c < 19968
                ? (c < 13312
                  ? (c >= 12784 && c <= 12799)
                  : (c <= 13312 || c == 19903))
                : (c <= 19968 || (c < 42192
                  ? (c >= 40959 && c <= 42124)
                  : c <= 42237)))
              : (c <= 42508 || (c < 42623
                ? (c < 42538
                  ? (c >= 42512 && c <= 42527)
                  : (c <= 42539 || (c >= 42560 && c <= 42606)))
                : (c <= 42653 || (c < 42775
                  ? (c >= 42656 && c <= 42725)
                  : c <= 42783)))))
            : (c <= 42888 || (c < 43015
              ? (c < 42965
                ? (c < 42960
                  ? (c >= 42891 && c <= 42954)
                  : (c <= 42961 || c == 42963))
                : (c <= 42969 || (c < 43011
                  ? (c >= 42994 && c <= 43009)
                  : c <= 43013)))
              : (c <= 43018 || (c < 43138
                ? (c < 43072
                  ? (c >= 43020 && c <= 43042)
                  : c <= 43123)
                : (c <= 43187 || (c < 43259
                  ? (c >= 43250 && c <= 43255)
                  : c <= 43259)))))))))))
      : (c <= 43262 || (c < 65345
        ? (c < 43816
          ? (c < 43646
            ? (c < 43494
              ? (c < 43396
                ? (c < 43312
                  ? (c >= 43274 && c <= 43301)
                  : (c <= 43334 || (c >= 43360 && c <= 43388)))
                : (c <= 43442 || (c < 43488
                  ? c == 43471
                  : c <= 43492)))
              : (c <= 43503 || (c < 43588
                ? (c < 43520
                  ? (c >= 43514 && c <= 43518)
                  : (c <= 43560 || (c >= 43584 && c <= 43586)))
                : (c <= 43595 || (c < 43642
                  ? (c >= 43616 && c <= 43638)
                  : c <= 43642)))))
            : (c <= 43695 || (c < 43744
              ? (c < 43712
                ? (c < 43701
                  ? c == 43697
                  : (c <= 43702 || (c >= 43705 && c <= 43709)))
                : (c <= 43712 || (c < 43739
                  ? c == 43714
                  : c <= 43741)))
              : (c <= 43754 || (c < 43785
                ? (c < 43777
                  ? (c >= 43762 && c <= 43764)
                  : c <= 43782)
                : (c <= 43790 || (c < 43808
                  ? (c >= 43793 && c <= 43798)
                  : c <= 43814)))))))
          : (c <= 43822 || (c < 64298
            ? (c < 55243
              ? (c < 44032
                ? (c < 43868
                  ? (c >= 43824 && c <= 43866)
                  : (c <= 43881 || (c >= 43888 && c <= 44002)))
                : (c <= 44032 || (c < 55216
                  ? c == 55203
                  : c <= 55238)))
              : (c <= 55291 || (c < 64275
                ? (c < 64112
                  ? (c >= 63744 && c <= 64109)
                  : (c <= 64217 || (c >= 64256 && c <= 64262)))
                : (c <= 64279 || (c < 64287
                  ? c == 64285
                  : c <= 64296)))))
            : (c <= 64310 || (c < 64848
              ? (c < 64323
                ? (c < 64318
                  ? (c >= 64312 && c <= 64316)
                  : (c <= 64318 || (c >= 64320 && c <= 64321)))
                : (c <= 64324 || (c < 64467
                  ? (c >= 64326 && c <= 64433)
                  : c <= 64829)))
              : (c <= 64911 || (c < 65136
                ? (c < 65008
                  ? (c >= 64914 && c <= 64967)
                  : c <= 65019)
                : (c <= 65140 || (c < 65313
                  ? (c >= 65142 && c <= 65276)
                  : c <= 65338)))))))))
        : (c <= 65370 || (c < 66928
          ? (c < 66208
            ? (c < 65549
              ? (c < 65490
                ? (c < 65474
                  ? (c >= 65382 && c <= 65470)
                  : (c <= 65479 || (c >= 65482 && c <= 65487)))
                : (c <= 65495 || (c < 65536
                  ? (c >= 65498 && c <= 65500)
                  : c <= 65547)))
              : (c <= 65574 || (c < 65616
                ? (c < 65596
                  ? (c >= 65576 && c <= 65594)
                  : (c <= 65597 || (c >= 65599 && c <= 65613)))
                : (c <= 65629 || (c < 66176
                  ? (c >= 65664 && c <= 65786)
                  : c <= 66204)))))
            : (c <= 66256 || (c < 66504
              ? (c < 66384
                ? (c < 66349
                  ? (c >= 66304 && c <= 66335)
                  : (c <= 66368 || (c >= 66370 && c <= 66377)))
                : (c <= 66421 || (c < 66464
                  ? (c >= 66432 && c <= 66461)
                  : c <= 66499)))
              : (c <= 66511 || (c < 66776
                ? (c < 66736
                  ? (c >= 66560 && c <= 66717)
                  : c <= 66771)
                : (c <= 66811 || (c < 66864
                  ? (c >= 66816 && c <= 66855)
                  : c <= 66915)))))))
          : (c <= 66938 || (c < 67506
            ? (c < 67003
              ? (c < 66967
                ? (c < 66956
                  ? (c >= 66940 && c <= 66954)
                  : (c <= 66962 || (c >= 66964 && c <= 66965)))
                : (c <= 66977 || (c < 66995
                  ? (c >= 66979 && c <= 66993)
                  : c <= 67001)))
              : (c <= 67004 || (c < 67424
                ? (c < 67392
                  ? (c >= 67072 && c <= 67382)
                  : c <= 67413)
                : (c <= 67431 || (c < 67463
                  ? (c >= 67456 && c <= 67461)
                  : c <= 67504)))))
            : (c <= 67514 || (c < 67680
              ? (c < 67639
                ? (c < 67592
                  ? (c >= 67584 && c <= 67589)
                  : (c <= 67592 || (c >= 67594 && c <= 67637)))
                : (c <= 67640 || (c < 67647
                  ? c == 67644
                  : c <= 67669)))
              : (c <= 67702 || (c < 67828
                ? (c < 67808
                  ? (c >= 67712 && c <= 67742)
                  : c <= 67826)
                : (c <= 67829 || (c < 67872
                  ? (c >= 67840 && c <= 67861)
                  : c <= 67883)))))))))))))));
}

static inline bool sym_identifier_character_set_2(int32_t c) {
  return (c < 6656
    ? (c < 2979
      ? (c < 2308
        ? (c < 1376
          ? (c < 750
            ? (c < 186
              ? (c < 'a'
                ? (c < 'A'
                  ? c == '$'
                  : (c <= 'Z' || c == '_'))
                : (c <= 'z' || (c < 181
                  ? c == 170
                  : c <= 181)))
              : (c <= 186 || (c < 710
                ? (c < 216
                  ? (c >= 192 && c <= 214)
                  : (c <= 246 || (c >= 248 && c <= 705)))
                : (c <= 721 || (c < 748
                  ? (c >= 736 && c <= 740)
                  : c <= 748)))))
            : (c <= 750 || (c < 908
              ? (c < 895
                ? (c < 886
                  ? (c >= 880 && c <= 884)
                  : (c <= 887 || (c >= 890 && c <= 893)))
                : (c <= 895 || (c < 904
                  ? c == 902
                  : c <= 906)))
              : (c <= 908 || (c < 1162
                ? (c < 931
                  ? (c >= 910 && c <= 929)
                  : (c <= 1013 || (c >= 1015 && c <= 1153)))
                : (c <= 1327 || (c < 1369
                  ? (c >= 1329 && c <= 1366)
                  : c <= 1369)))))))
          : (c <= 1416 || (c < 1969
            ? (c < 1765
              ? (c < 1646
                ? (c < 1519
                  ? (c >= 1488 && c <= 1514)
                  : (c <= 1522 || (c >= 1568 && c <= 1610)))
                : (c <= 1647 || (c < 1749
                  ? (c >= 1649 && c <= 1747)
                  : c <= 1749)))
              : (c <= 1766 || (c < 1808
                ? (c < 1786
                  ? (c >= 1774 && c <= 1775)
                  : (c <= 1788 || c == 1791))
                : (c <= 1808 || (c < 1869
                  ? (c >= 1810 && c <= 1839)
                  : c <= 1957)))))
            : (c <= 1969 || (c < 2088
              ? (c < 2048
                ? (c < 2036
                  ? (c >= 1994 && c <= 2026)
                  : (c <= 2037 || c == 2042))
                : (c <= 2069 || (c < 2084
                  ? c == 2074
                  : c <= 2084)))
              : (c <= 2088 || (c < 2160
                ? (c < 2144
                  ? (c >= 2112 && c <= 2136)
                  : c <= 2154)
                : (c <= 2183 || (c < 2208
                  ? (c >= 2185 && c <= 2190)
                  : c <= 2249)))))))))
        : (c <= 2361 || (c < 2693
          ? (c < 2527
            ? (c < 2451
              ? (c < 2417
                ? (c < 2384
                  ? c == 2365
                  : (c <= 2384 || (c >= 2392 && c <= 2401)))
                : (c <= 2432 || (c < 2447
                  ? (c >= 2437 && c <= 2444)
                  : c <= 2448)))
              : (c <= 2472 || (c < 2493
                ? (c < 2482
                  ? (c >= 2474 && c <= 2480)
                  : (c <= 2482 || (c >= 2486 && c <= 2489)))
                : (c <= 2493 || (c < 2524
                  ? c == 2510
                  : c <= 2525)))))
            : (c <= 2529 || (c < 2610
              ? (c < 2575
                ? (c < 2556
                  ? (c >= 2544 && c <= 2545)
                  : (c <= 2556 || (c >= 2565 && c <= 2570)))
                : (c <= 2576 || (c < 2602
                  ? (c >= 2579 && c <= 2600)
                  : c <= 2608)))
              : (c <= 2611 || (c < 2649
                ? (c < 2616
                  ? (c >= 2613 && c <= 2614)
                  : c <= 2617)
                : (c <= 2652 || (c < 2674
                  ? c == 2654
                  : c <= 2676)))))))
          : (c <= 2701 || (c < 2866
            ? (c < 2768
              ? (c < 2738
                ? (c < 2707
                  ? (c >= 2703 && c <= 2705)
                  : (c <= 2728 || (c >= 2730 && c <= 2736)))
                : (c <= 2739 || (c < 2749
                  ? (c >= 2741 && c <= 2745)
                  : c <= 2749)))
              : (c <= 2768 || (c < 2831
                ? (c < 2809
                  ? (c >= 2784 && c <= 2785)
                  : (c <= 2809 || (c >= 2821 && c <= 2828)))
                : (c <= 2832 || (c < 2858
                  ? (c >= 2835 && c <= 2856)
                  : c <= 2864)))))
            : (c <= 2867 || (c < 2949
              ? (c < 2911
                ? (c < 2877
                  ? (c >= 2869 && c <= 2873)
                  : (c <= 2877 || (c >= 2908 && c <= 2909)))
                : (c <= 2913 || (c < 2947
                  ? c == 2929
                  : c <= 2947)))
              : (c <= 2954 || (c < 2969
                ? (c < 2962
                  ? (c >= 2958 && c <= 2960)
                  : c <= 2965)
                : (c <= 2970 || (c < 2974
                  ? c == 2972
                  : c <= 2975)))))))))))
      : (c <= 2980 || (c < 4159
        ? (c < 3412
          ? (c < 3214
            ? (c < 3114
              ? (c < 3077
                ? (c < 2990
                  ? (c >= 2984 && c <= 2986)
                  : (c <= 3001 || c == 3024))
                : (c <= 3084 || (c < 3090
                  ? (c >= 3086 && c <= 3088)
                  : c <= 3112)))
              : (c <= 3129 || (c < 3168
                ? (c < 3160
                  ? c == 3133
                  : (c <= 3162 || c == 3165))
                : (c <= 3169 || (c < 3205
                  ? c == 3200
                  : c <= 3212)))))
            : (c <= 3216 || (c < 3313
              ? (c < 3261
                ? (c < 3242
                  ? (c >= 3218 && c <= 3240)
                  : (c <= 3251 || (c >= 3253 && c <= 3257)))
                : (c <= 3261 || (c < 3296
                  ? (c >= 3293 && c <= 3294)
                  : c <= 3297)))
              : (c <= 3314 || (c < 3346
                ? (c < 3342
                  ? (c >= 3332 && c <= 3340)
                  : c <= 3344)
                : (c <= 3386 || (c < 3406
                  ? c == 3389
                  : c <= 3406)))))))
          : (c <= 3414 || (c < 3724
            ? (c < 3520
              ? (c < 3482
                ? (c < 3450
                  ? (c >= 3423 && c <= 3425)
                  : (c <= 3455 || (c >= 3461 && c <= 3478)))
                : (c <= 3505 || (c < 3517
                  ? (c >= 3507 && c <= 3515)
                  : c <= 3517)))
              : (c <= 3526 || (c < 3713
                ? (c < 3634
                  ? (c >= 3585 && c <= 3632)
                  : (c <= 3635 || (c >= 3648 && c <= 3654)))
                : (c <= 3714 || (c < 3718
                  ? c == 3716
                  : c <= 3722)))))
            : (c <= 3747 || (c < 3804
              ? (c < 3773
                ? (c < 3751
                  ? c == 3749
                  : (c <= 3760 || (c >= 3762 && c <= 3763)))
                : (c <= 3773 || (c < 3782
                  ? (c >= 3776 && c <= 3780)
                  : c <= 3782)))
              : (c <= 3807 || (c < 3913
                ? (c < 3904
                  ? c == 3840
                  : c <= 3911)
                : (c <= 3948 || (c < 4096
                  ? (c >= 3976 && c <= 3980)
                  : c <= 4138)))))))))
        : (c <= 4159 || (c < 4888
          ? (c < 4688
            ? (c < 4238
              ? (c < 4197
                ? (c < 4186
                  ? (c >= 4176 && c <= 4181)
                  : (c <= 4189 || c == 4193))
                : (c <= 4198 || (c < 4213
                  ? (c >= 4206 && c <= 4208)
                  : c <= 4225)))
              : (c <= 4238 || (c < 4304
                ? (c < 4295
                  ? (c >= 4256 && c <= 4293)
                  : (c <= 4295 || c == 4301))
                : (c <= 4346 || (c < 4682
                  ? (c >= 4348 && c <= 4680)
                  : c <= 4685)))))
            : (c <= 4694 || (c < 4792
              ? (c < 4746
                ? (c < 4698
                  ? c == 4696
                  : (c <= 4701 || (c >= 4704 && c <= 4744)))
                : (c <= 4749 || (c < 4786
                  ? (c >= 4752 && c <= 4784)
                  : c <= 4789)))
              : (c <= 4798 || (c < 4808
                ? (c < 4802
                  ? c == 4800
                  : c <= 4805)
                : (c <= 4822 || (c < 4882
                  ? (c >= 4824 && c <= 4880)
                  : c <= 4885)))))))
          : (c <= 4954 || (c < 6016
            ? (c < 5792
              ? (c < 5121
                ? (c < 5024
                  ? (c >= 4992 && c <= 5007)
                  : (c <= 5109 || (c >= 5112 && c <= 5117)))
                : (c <= 5740 || (c < 5761
                  ? (c >= 5743 && c <= 5759)
                  : c <= 5786)))
              : (c <= 5866 || (c < 5952
                ? (c < 5888
                  ? (c >= 5873 && c <= 5880)
                  : (c <= 5905 || (c >= 5919 && c <= 5937)))
                : (c <= 5969 || (c < 5998
                  ? (c >= 5984 && c <= 5996)
                  : c <= 6000)))))
            : (c <= 6067 || (c < 6320
              ? (c < 6272
                ? (c < 6108
                  ? c == 6103
                  : (c <= 6108 || (c >= 6176 && c <= 6264)))
                : (c <= 6276 || (c < 6314
                  ? (c >= 6279 && c <= 6312)
                  : c <= 6314)))
              : (c <= 6389 || (c < 6512
                ? (c < 6480
                  ? (c >= 6400 && c <= 6430)
                  : c <= 6509)
                : (c <= 6516 || (c < 6576
                  ? (c >= 6528 && c <= 6571)
                  : c <= 6601)))))))))))))
    : (c <= 6678 || (c < 43259
      ? (c < 8579
        ? (c < 8031
          ? (c < 7401
            ? (c < 7098
              ? (c < 6981
                ? (c < 6823
                  ? (c >= 6688 && c <= 6740)
                  : (c <= 6823 || (c >= 6917 && c <= 6963)))
                : (c <= 6988 || (c < 7086
                  ? (c >= 7043 && c <= 7072)
                  : c <= 7087)))
              : (c <= 7141 || (c < 7296
                ? (c < 7245
                  ? (c >= 7168 && c <= 7203)
                  : (c <= 7247 || (c >= 7258 && c <= 7293)))
                : (c <= 7304 || (c < 7357
                  ? (c >= 7312 && c <= 7354)
                  : c <= 7359)))))
            : (c <= 7404 || (c < 7968
              ? (c < 7424
                ? (c < 7413
                  ? (c >= 7406 && c <= 7411)
                  : (c <= 7414 || c == 7418))
                : (c <= 7615 || (c < 7960
                  ? (c >= 7680 && c <= 7957)
                  : c <= 7965)))
              : (c <= 8005 || (c < 8025
                ? (c < 8016
                  ? (c >= 8008 && c <= 8013)
                  : c <= 8023)
                : (c <= 8025 || (c < 8029
                  ? c == 8027
                  : c <= 8029)))))))
          : (c <= 8061 || (c < 8450
            ? (c < 8150
              ? (c < 8130
                ? (c < 8118
                  ? (c >= 8064 && c <= 8116)
                  : (c <= 8124 || c == 8126))
                : (c <= 8132 || (c < 8144
                  ? (c >= 8134 && c <= 8140)
                  : c <= 8147)))
              : (c <= 8155 || (c < 8305
                ? (c < 8178
                  ? (c >= 8160 && c <= 8172)
                  : (c <= 8180 || (c >= 8182 && c <= 8188)))
                : (c <= 8305 || (c < 8336
                  ? c == 8319
                  : c <= 8348)))))
            : (c <= 8450 || (c < 8488
              ? (c < 8473
                ? (c < 8458
                  ? c == 8455
                  : (c <= 8467 || c == 8469))
                : (c <= 8477 || (c < 8486
                  ? c == 8484
                  : c <= 8486)))
              : (c <= 8488 || (c < 8508
                ? (c < 8495
                  ? (c >= 8490 && c <= 8493)
                  : c <= 8505)
                : (c <= 8511 || (c < 8526
                  ? (c >= 8517 && c <= 8521)
                  : c <= 8526)))))))))
        : (c <= 8580 || (c < 12593
          ? (c < 11712
            ? (c < 11568
              ? (c < 11520
                ? (c < 11499
                  ? (c >= 11264 && c <= 11492)
                  : (c <= 11502 || (c >= 11506 && c <= 11507)))
                : (c <= 11557 || (c < 11565
                  ? c == 11559
                  : c <= 11565)))
              : (c <= 11623 || (c < 11688
                ? (c < 11648
                  ? c == 11631
                  : (c <= 11670 || (c >= 11680 && c <= 11686)))
                : (c <= 11694 || (c < 11704
                  ? (c >= 11696 && c <= 11702)
                  : c <= 11710)))))
            : (c <= 11718 || (c < 12347
              ? (c < 11823
                ? (c < 11728
                  ? (c >= 11720 && c <= 11726)
                  : (c <= 11734 || (c >= 11736 && c <= 11742)))
                : (c <= 11823 || (c < 12337
                  ? (c >= 12293 && c <= 12294)
                  : c <= 12341)))
              : (c <= 12348 || (c < 12449
                ? (c < 12445
                  ? (c >= 12353 && c <= 12438)
                  : c <= 12447)
                : (c <= 12538 || (c < 12549
                  ? (c >= 12540 && c <= 12543)
                  : c <= 12591)))))))
          : (c <= 12686 || (c < 42775
            ? (c < 42192
              ? (c < 19903
                ? (c < 12784
                  ? (c >= 12704 && c <= 12735)
                  : (c <= 12799 || c == 13312))
                : (c <= 19903 || (c < 40959
                  ? c == 19968
                  : c <= 42124)))
              : (c <= 42237 || (c < 42560
                ? (c < 42512
                  ? (c >= 42240 && c <= 42508)
                  : (c <= 42527 || (c >= 42538 && c <= 42539)))
                : (c <= 42606 || (c < 42656
                  ? (c >= 42623 && c <= 42653)
                  : c <= 42725)))))
            : (c <= 42783 || (c < 43011
              ? (c < 42963
                ? (c < 42891
                  ? (c >= 42786 && c <= 42888)
                  : (c <= 42954 || (c >= 42960 && c <= 42961)))
                : (c <= 42963 || (c < 42994
                  ? (c >= 42965 && c <= 42969)
                  : c <= 43009)))
              : (c <= 43013 || (c < 43072
                ? (c < 43020
                  ? (c >= 43015 && c <= 43018)
                  : c <= 43042)
                : (c <= 43123 || (c < 43250
                  ? (c >= 43138 && c <= 43187)
                  : c <= 43255)))))))))))
      : (c <= 43259 || (c < 65313
        ? (c < 43808
          ? (c < 43642
            ? (c < 43488
              ? (c < 43360
                ? (c < 43274
                  ? (c >= 43261 && c <= 43262)
                  : (c <= 43301 || (c >= 43312 && c <= 43334)))
                : (c <= 43388 || (c < 43471
                  ? (c >= 43396 && c <= 43442)
                  : c <= 43471)))
              : (c <= 43492 || (c < 43584
                ? (c < 43514
                  ? (c >= 43494 && c <= 43503)
                  : (c <= 43518 || (c >= 43520 && c <= 43560)))
                : (c <= 43586 || (c < 43616
                  ? (c >= 43588 && c <= 43595)
                  : c <= 43638)))))
            : (c <= 43642 || (c < 43739
              ? (c < 43705
                ? (c < 43697
                  ? (c >= 43646 && c <= 43695)
                  : (c <= 43697 || (c >= 43701 && c <= 43702)))
                : (c <= 43709 || (c < 43714
                  ? c == 43712
                  : c <= 43714)))
              : (c <= 43741 || (c < 43777
                ? (c < 43762
                  ? (c >= 43744 && c <= 43754)
                  : c <= 43764)
                : (c <= 43782 || (c < 43793
                  ? (c >= 43785 && c <= 43790)
                  : c <= 43798)))))))
          : (c <= 43814 || (c < 64287
            ? (c < 55216
              ? (c < 43888
                ? (c < 43824
                  ? (c >= 43816 && c <= 43822)
                  : (c <= 43866 || (c >= 43868 && c <= 43881)))
                : (c <= 44002 || (c < 55203
                  ? c == 44032
                  : c <= 55203)))
              : (c <= 55238 || (c < 64256
                ? (c < 63744
                  ? (c >= 55243 && c <= 55291)
                  : (c <= 64109 || (c >= 64112 && c <= 64217)))
                : (c <= 64262 || (c < 64285
                  ? (c >= 64275 && c <= 64279)
                  : c <= 64285)))))
            : (c <= 64296 || (c < 64467
              ? (c < 64320
                ? (c < 64312
                  ? (c >= 64298 && c <= 64310)
                  : (c <= 64316 || c == 64318))
                : (c <= 64321 || (c < 64326
                  ? (c >= 64323 && c <= 64324)
                  : c <= 64433)))
              : (c <= 64829 || (c < 65008
                ? (c < 64914
                  ? (c >= 64848 && c <= 64911)
                  : c <= 64967)
                : (c <= 65019 || (c < 65142
                  ? (c >= 65136 && c <= 65140)
                  : c <= 65276)))))))))
        : (c <= 65338 || (c < 66864
          ? (c < 66176
            ? (c < 65536
              ? (c < 65482
                ? (c < 65382
                  ? (c >= 65345 && c <= 65370)
                  : (c <= 65470 || (c >= 65474 && c <= 65479)))
                : (c <= 65487 || (c < 65498
                  ? (c >= 65490 && c <= 65495)
                  : c <= 65500)))
              : (c <= 65547 || (c < 65599
                ? (c < 65576
                  ? (c >= 65549 && c <= 65574)
                  : (c <= 65594 || (c >= 65596 && c <= 65597)))
                : (c <= 65613 || (c < 65664
                  ? (c >= 65616 && c <= 65629)
                  : c <= 65786)))))
            : (c <= 66204 || (c < 66464
              ? (c < 66370
                ? (c < 66304
                  ? (c >= 66208 && c <= 66256)
                  : (c <= 66335 || (c >= 66349 && c <= 66368)))
                : (c <= 66377 || (c < 66432
                  ? (c >= 66384 && c <= 66421)
                  : c <= 66461)))
              : (c <= 66499 || (c < 66736
                ? (c < 66560
                  ? (c >= 66504 && c <= 66511)
                  : c <= 66717)
                : (c <= 66771 || (c < 66816
                  ? (c >= 66776 && c <= 66811)
                  : c <= 66855)))))))
          : (c <= 66915 || (c < 67506
            ? (c < 66995
              ? (c < 66964
                ? (c < 66940
                  ? (c >= 66928 && c <= 66938)
                  : (c <= 66954 || (c >= 66956 && c <= 66962)))
                : (c <= 66965 || (c < 66979
                  ? (c >= 66967 && c <= 66977)
                  : c <= 66993)))
              : (c <= 67001 || (c < 67424
                ? (c < 67072
                  ? (c >= 67003 && c <= 67004)
                  : (c <= 67382 || (c >= 67392 && c <= 67413)))
                : (c <= 67431 || (c < 67463
                  ? (c >= 67456 && c <= 67461)
                  : c <= 67504)))))
            : (c <= 67514 || (c < 67680
              ? (c < 67639
                ? (c < 67592
                  ? (c >= 67584 && c <= 67589)
                  : (c <= 67592 || (c >= 67594 && c <= 67637)))
                : (c <= 67640 || (c < 67647
                  ? c == 67644
                  : c <= 67669)))
              : (c <= 67702 || (c < 67828
                ? (c < 67808
                  ? (c >= 67712 && c <= 67742)
                  : c <= 67826)
                : (c <= 67829 || (c < 67872
                  ? (c >= 67840 && c <= 67861)
                  : c <= 67883)))))))))))))));
}

static inline bool sym_identifier_character_set_3(int32_t c) {
  return (c < 6656
    ? (c < 2979
      ? (c < 2308
        ? (c < 1376
          ? (c < 880
            ? (c < 192
              ? (c < 170
                ? (c < '_'
                  ? (c >= '$' && c <= 'Z')
                  : (c <= '_' || (c >= 'a' && c <= 'z')))
                : (c <= 170 || (c < 186
                  ? c == 181
                  : c <= 186)))
              : (c <= 214 || (c < 736
                ? (c < 248
                  ? (c >= 216 && c <= 246)
                  : (c <= 705 || (c >= 710 && c <= 721)))
                : (c <= 740 || (c < 750
                  ? c == 748
                  : c <= 750)))))
            : (c <= 884 || (c < 910
              ? (c < 902
                ? (c < 890
                  ? (c >= 886 && c <= 887)
                  : (c <= 893 || c == 895))
                : (c <= 902 || (c < 908
                  ? (c >= 904 && c <= 906)
                  : c <= 908)))
              : (c <= 929 || (c < 1162
                ? (c < 1015
                  ? (c >= 931 && c <= 1013)
                  : c <= 1153)
                : (c <= 1327 || (c < 1369
                  ? (c >= 1329 && c <= 1366)
                  : c <= 1369)))))))
          : (c <= 1416 || (c < 1969
            ? (c < 1765
              ? (c < 1646
                ? (c < 1519
                  ? (c >= 1488 && c <= 1514)
                  : (c <= 1522 || (c >= 1568 && c <= 1610)))
                : (c <= 1647 || (c < 1749
                  ? (c >= 1649 && c <= 1747)
                  : c <= 1749)))
              : (c <= 1766 || (c < 1808
                ? (c < 1786
                  ? (c >= 1774 && c <= 1775)
                  : (c <= 1788 || c == 1791))
                : (c <= 1808 || (c < 1869
                  ? (c >= 1810 && c <= 1839)
                  : c <= 1957)))))
            : (c <= 1969 || (c < 2088
              ? (c < 2048
                ? (c < 2036
                  ? (c >= 1994 && c <= 2026)
                  : (c <= 2037 || c == 2042))
                : (c <= 2069 || (c < 2084
                  ? c == 2074
                  : c <= 2084)))
              : (c <= 2088 || (c < 2160
                ? (c < 2144
                  ? (c >= 2112 && c <= 2136)
                  : c <= 2154)
                : (c <= 2183 || (c < 2208
                  ? (c >= 2185 && c <= 2190)
                  : c <= 2249)))))))))
        : (c <= 2361 || (c < 2693
          ? (c < 2527
            ? (c < 2451
              ? (c < 2417
                ? (c < 2384
                  ? c == 2365
                  : (c <= 2384 || (c >= 2392 && c <= 2401)))
                : (c <= 2432 || (c < 2447
                  ? (c >= 2437 && c <= 2444)
                  : c <= 2448)))
              : (c <= 2472 || (c < 2493
                ? (c < 2482
                  ? (c >= 2474 && c <= 2480)
                  : (c <= 2482 || (c >= 2486 && c <= 2489)))
                : (c <= 2493 || (c < 2524
                  ? c == 2510
                  : c <= 2525)))))
            : (c <= 2529 || (c < 2610
              ? (c < 2575
                ? (c < 2556
                  ? (c >= 2544 && c <= 2545)
                  : (c <= 2556 || (c >= 2565 && c <= 2570)))
                : (c <= 2576 || (c < 2602
                  ? (c >= 2579 && c <= 2600)
                  : c <= 2608)))
              : (c <= 2611 || (c < 2649
                ? (c < 2616
                  ? (c >= 2613 && c <= 2614)
                  : c <= 2617)
                : (c <= 2652 || (c < 2674
                  ? c == 2654
                  : c <= 2676)))))))
          : (c <= 2701 || (c < 2866
            ? (c < 2768
              ? (c < 2738
                ? (c < 2707
                  ? (c >= 2703 && c <= 2705)
                  : (c <= 2728 || (c >= 2730 && c <= 2736)))
                : (c <= 2739 || (c < 2749
                  ? (c >= 2741 && c <= 2745)
                  : c <= 2749)))
              : (c <= 2768 || (c < 2831
                ? (c < 2809
                  ? (c >= 2784 && c <= 2785)
                  : (c <= 2809 || (c >= 2821 && c <= 2828)))
                : (c <= 2832 || (c < 2858
                  ? (c >= 2835 && c <= 2856)
                  : c <= 2864)))))
            : (c <= 2867 || (c < 2949
              ? (c < 2911
                ? (c < 2877
                  ? (c >= 2869 && c <= 2873)
                  : (c <= 2877 || (c >= 2908 && c <= 2909)))
                : (c <= 2913 || (c < 2947
                  ? c == 2929
                  : c <= 2947)))
              : (c <= 2954 || (c < 2969
                ? (c < 2962
                  ? (c >= 2958 && c <= 2960)
                  : c <= 2965)
                : (c <= 2970 || (c < 2974
                  ? c == 2972
                  : c <= 2975)))))))))))
      : (c <= 2980 || (c < 4159
        ? (c < 3412
          ? (c < 3214
            ? (c < 3114
              ? (c < 3077
                ? (c < 2990
                  ? (c >= 2984 && c <= 2986)
                  : (c <= 3001 || c == 3024))
                : (c <= 3084 || (c < 3090
                  ? (c >= 3086 && c <= 3088)
                  : c <= 3112)))
              : (c <= 3129 || (c < 3168
                ? (c < 3160
                  ? c == 3133
                  : (c <= 3162 || c == 3165))
                : (c <= 3169 || (c < 3205
                  ? c == 3200
                  : c <= 3212)))))
            : (c <= 3216 || (c < 3313
              ? (c < 3261
                ? (c < 3242
                  ? (c >= 3218 && c <= 3240)
                  : (c <= 3251 || (c >= 3253 && c <= 3257)))
                : (c <= 3261 || (c < 3296
                  ? (c >= 3293 && c <= 3294)
                  : c <= 3297)))
              : (c <= 3314 || (c < 3346
                ? (c < 3342
                  ? (c >= 3332 && c <= 3340)
                  : c <= 3344)
                : (c <= 3386 || (c < 3406
                  ? c == 3389
                  : c <= 3406)))))))
          : (c <= 3414 || (c < 3724
            ? (c < 3520
              ? (c < 3482
                ? (c < 3450
                  ? (c >= 3423 && c <= 3425)
                  : (c <= 3455 || (c >= 3461 && c <= 3478)))
                : (c <= 3505 || (c < 3517
                  ? (c >= 3507 && c <= 3515)
                  : c <= 3517)))
              : (c <= 3526 || (c < 3713
                ? (c < 3634
                  ? (c >= 3585 && c <= 3632)
                  : (c <= 3635 || (c >= 3648 && c <= 3654)))
                : (c <= 3714 || (c < 3718
                  ? c == 3716
                  : c <= 3722)))))
            : (c <= 3747 || (c < 3804
              ? (c < 3773
                ? (c < 3751
                  ? c == 3749
                  : (c <= 3760 || (c >= 3762 && c <= 3763)))
                : (c <= 3773 || (c < 3782
                  ? (c >= 3776 && c <= 3780)
                  : c <= 3782)))
              : (c <= 3807 || (c < 3913
                ? (c < 3904
                  ? c == 3840
                  : c <= 3911)
                : (c <= 3948 || (c < 4096
                  ? (c >= 3976 && c <= 3980)
                  : c <= 4138)))))))))
        : (c <= 4159 || (c < 4888
          ? (c < 4688
            ? (c < 4238
              ? (c < 4197
                ? (c < 4186
                  ? (c >= 4176 && c <= 4181)
                  : (c <= 4189 || c == 4193))
                : (c <= 4198 || (c < 4213
                  ? (c >= 4206 && c <= 4208)
                  : c <= 4225)))
              : (c <= 4238 || (c < 4304
                ? (c < 4295
                  ? (c >= 4256 && c <= 4293)
                  : (c <= 4295 || c == 4301))
                : (c <= 4346 || (c < 4682
                  ? (c >= 4348 && c <= 4680)
                  : c <= 4685)))))
            : (c <= 4694 || (c < 4792
              ? (c < 4746
                ? (c < 4698
                  ? c == 4696
                  : (c <= 4701 || (c >= 4704 && c <= 4744)))
                : (c <= 4749 || (c < 4786
                  ? (c >= 4752 && c <= 4784)
                  : c <= 4789)))
              : (c <= 4798 || (c < 4808
                ? (c < 4802
                  ? c == 4800
                  : c <= 4805)
                : (c <= 4822 || (c < 4882
                  ? (c >= 4824 && c <= 4880)
                  : c <= 4885)))))))
          : (c <= 4954 || (c < 6016
            ? (c < 5792
              ? (c < 5121
                ? (c < 5024
                  ? (c >= 4992 && c <= 5007)
                  : (c <= 5109 || (c >= 5112 && c <= 5117)))
                : (c <= 5740 || (c < 5761
                  ? (c >= 5743 && c <= 5759)
                  : c <= 5786)))
              : (c <= 5866 || (c < 5952
                ? (c < 5888
                  ? (c >= 5873 && c <= 5880)
                  : (c <= 5905 || (c >= 5919 && c <= 5937)))
                : (c <= 5969 || (c < 5998
                  ? (c >= 5984 && c <= 5996)
                  : c <= 6000)))))
            : (c <= 6067 || (c < 6320
              ? (c < 6272
                ? (c < 6108
                  ? c == 6103
                  : (c <= 6108 || (c >= 6176 && c <= 6264)))
                : (c <= 6276 || (c < 6314
                  ? (c >= 6279 && c <= 6312)
                  : c <= 6314)))
              : (c <= 6389 || (c < 6512
                ? (c < 6480
                  ? (c >= 6400 && c <= 6430)
                  : c <= 6509)
                : (c <= 6516 || (c < 6576
                  ? (c >= 6528 && c <= 6571)
                  : c <= 6601)))))))))))))
    : (c <= 6678 || (c < 43259
      ? (c < 8579
        ? (c < 8031
          ? (c < 7401
            ? (c < 7098
              ? (c < 6981
                ? (c < 6823
                  ? (c >= 6688 && c <= 6740)
                  : (c <= 6823 || (c >= 6917 && c <= 6963)))
                : (c <= 6988 || (c < 7086
                  ? (c >= 7043 && c <= 7072)
                  : c <= 7087)))
              : (c <= 7141 || (c < 7296
                ? (c < 7245
                  ? (c >= 7168 && c <= 7203)
                  : (c <= 7247 || (c >= 7258 && c <= 7293)))
                : (c <= 7304 || (c < 7357
                  ? (c >= 7312 && c <= 7354)
                  : c <= 7359)))))
            : (c <= 7404 || (c < 7968
              ? (c < 7424
                ? (c < 7413
                  ? (c >= 7406 && c <= 7411)
                  : (c <= 7414 || c == 7418))
                : (c <= 7615 || (c < 7960
                  ? (c >= 7680 && c <= 7957)
                  : c <= 7965)))
              : (c <= 8005 || (c < 8025
                ? (c < 8016
                  ? (c >= 8008 && c <= 8013)
                  : c <= 8023)
                : (c <= 8025 || (c < 8029
                  ? c == 8027
                  : c <= 8029)))))))
          : (c <= 8061 || (c < 8450
            ? (c < 8150
              ? (c < 8130
                ? (c < 8118
                  ? (c >= 8064 && c <= 8116)
                  : (c <= 8124 || c == 8126))
                : (c <= 8132 || (c < 8144
                  ? (c >= 8134 && c <= 8140)
                  : c <= 8147)))
              : (c <= 8155 || (c < 8305
                ? (c < 8178
                  ? (c >= 8160 && c <= 8172)
                  : (c <= 8180 || (c >= 8182 && c <= 8188)))
                : (c <= 8305 || (c < 8336
                  ? c == 8319
                  : c <= 8348)))))
            : (c <= 8450 || (c < 8488
              ? (c < 8473
                ? (c < 8458
                  ? c == 8455
                  : (c <= 8467 || c == 8469))
                : (c <= 8477 || (c < 8486
                  ? c == 8484
                  : c <= 8486)))
              : (c <= 8488 || (c < 8508
                ? (c < 8495
                  ? (c >= 8490 && c <= 8493)
                  : c <= 8505)
                : (c <= 8511 || (c < 8526
                  ? (c >= 8517 && c <= 8521)
                  : c <= 8526)))))))))
        : (c <= 8580 || (c < 12593
          ? (c < 11712
            ? (c < 11568
              ? (c < 11520
                ? (c < 11499
                  ? (c >= 11264 && c <= 11492)
                  : (c <= 11502 || (c >= 11506 && c <= 11507)))
                : (c <= 11557 || (c < 11565
                  ? c == 11559
                  : c <= 11565)))
              : (c <= 11623 || (c < 11688
                ? (c < 11648
                  ? c == 11631
                  : (c <= 11670 || (c >= 11680 && c <= 11686)))
                : (c <= 11694 || (c < 11704
                  ? (c >= 11696 && c <= 11702)
                  : c <= 11710)))))
            : (c <= 11718 || (c < 12347
              ? (c < 11823
                ? (c < 11728
                  ? (c >= 11720 && c <= 11726)
                  : (c <= 11734 || (c >= 11736 && c <= 11742)))
                : (c <= 11823 || (c < 12337
                  ? (c >= 12293 && c <= 12294)
                  : c <= 12341)))
              : (c <= 12348 || (c < 12449
                ? (c < 12445
                  ? (c >= 12353 && c <= 12438)
                  : c <= 12447)
                : (c <= 12538 || (c < 12549
                  ? (c >= 12540 && c <= 12543)
                  : c <= 12591)))))))
          : (c <= 12686 || (c < 42775
            ? (c < 42192
              ? (c < 19903
                ? (c < 12784
                  ? (c >= 12704 && c <= 12735)
                  : (c <= 12799 || c == 13312))
                : (c <= 19903 || (c < 40959
                  ? c == 19968
                  : c <= 42124)))
              : (c <= 42237 || (c < 42560
                ? (c < 42512
                  ? (c >= 42240 && c <= 42508)
                  : (c <= 42527 || (c >= 42538 && c <= 42539)))
                : (c <= 42606 || (c < 42656
                  ? (c >= 42623 && c <= 42653)
                  : c <= 42725)))))
            : (c <= 42783 || (c < 43011
              ? (c < 42963
                ? (c < 42891
                  ? (c >= 42786 && c <= 42888)
                  : (c <= 42954 || (c >= 42960 && c <= 42961)))
                : (c <= 42963 || (c < 42994
                  ? (c >= 42965 && c <= 42969)
                  : c <= 43009)))
              : (c <= 43013 || (c < 43072
                ? (c < 43020
                  ? (c >= 43015 && c <= 43018)
                  : c <= 43042)
                : (c <= 43123 || (c < 43250
                  ? (c >= 43138 && c <= 43187)
                  : c <= 43255)))))))))))
      : (c <= 43259 || (c < 65313
        ? (c < 43808
          ? (c < 43642
            ? (c < 43488
              ? (c < 43360
                ? (c < 43274
                  ? (c >= 43261 && c <= 43262)
                  : (c <= 43301 || (c >= 43312 && c <= 43334)))
                : (c <= 43388 || (c < 43471
                  ? (c >= 43396 && c <= 43442)
                  : c <= 43471)))
              : (c <= 43492 || (c < 43584
                ? (c < 43514
                  ? (c >= 43494 && c <= 43503)
                  : (c <= 43518 || (c >= 43520 && c <= 43560)))
                : (c <= 43586 || (c < 43616
                  ? (c >= 43588 && c <= 43595)
                  : c <= 43638)))))
            : (c <= 43642 || (c < 43739
              ? (c < 43705
                ? (c < 43697
                  ? (c >= 43646 && c <= 43695)
                  : (c <= 43697 || (c >= 43701 && c <= 43702)))
                : (c <= 43709 || (c < 43714
                  ? c == 43712
                  : c <= 43714)))
              : (c <= 43741 || (c < 43777
                ? (c < 43762
                  ? (c >= 43744 && c <= 43754)
                  : c <= 43764)
                : (c <= 43782 || (c < 43793
                  ? (c >= 43785 && c <= 43790)
                  : c <= 43798)))))))
          : (c <= 43814 || (c < 64287
            ? (c < 55216
              ? (c < 43888
                ? (c < 43824
                  ? (c >= 43816 && c <= 43822)
                  : (c <= 43866 || (c >= 43868 && c <= 43881)))
                : (c <= 44002 || (c < 55203
                  ? c == 44032
                  : c <= 55203)))
              : (c <= 55238 || (c < 64256
                ? (c < 63744
                  ? (c >= 55243 && c <= 55291)
                  : (c <= 64109 || (c >= 64112 && c <= 64217)))
                : (c <= 64262 || (c < 64285
                  ? (c >= 64275 && c <= 64279)
                  : c <= 64285)))))
            : (c <= 64296 || (c < 64467
              ? (c < 64320
                ? (c < 64312
                  ? (c >= 64298 && c <= 64310)
                  : (c <= 64316 || c == 64318))
                : (c <= 64321 || (c < 64326
                  ? (c >= 64323 && c <= 64324)
                  : c <= 64433)))
              : (c <= 64829 || (c < 65008
                ? (c < 64914
                  ? (c >= 64848 && c <= 64911)
                  : c <= 64967)
                : (c <= 65019 || (c < 65142
                  ? (c >= 65136 && c <= 65140)
                  : c <= 65276)))))))))
        : (c <= 65338 || (c < 66864
          ? (c < 66176
            ? (c < 65536
              ? (c < 65482
                ? (c < 65382
                  ? (c >= 65345 && c <= 65370)
                  : (c <= 65470 || (c >= 65474 && c <= 65479)))
                : (c <= 65487 || (c < 65498
                  ? (c >= 65490 && c <= 65495)
                  : c <= 65500)))
              : (c <= 65547 || (c < 65599
                ? (c < 65576
                  ? (c >= 65549 && c <= 65574)
                  : (c <= 65594 || (c >= 65596 && c <= 65597)))
                : (c <= 65613 || (c < 65664
                  ? (c >= 65616 && c <= 65629)
                  : c <= 65786)))))
            : (c <= 66204 || (c < 66464
              ? (c < 66370
                ? (c < 66304
                  ? (c >= 66208 && c <= 66256)
                  : (c <= 66335 || (c >= 66349 && c <= 66368)))
                : (c <= 66377 || (c < 66432
                  ? (c >= 66384 && c <= 66421)
                  : c <= 66461)))
              : (c <= 66499 || (c < 66736
                ? (c < 66560
                  ? (c >= 66504 && c <= 66511)
                  : c <= 66717)
                : (c <= 66771 || (c < 66816
                  ? (c >= 66776 && c <= 66811)
                  : c <= 66855)))))))
          : (c <= 66915 || (c < 67506
            ? (c < 66995
              ? (c < 66964
                ? (c < 66940
                  ? (c >= 66928 && c <= 66938)
                  : (c <= 66954 || (c >= 66956 && c <= 66962)))
                : (c <= 66965 || (c < 66979
                  ? (c >= 66967 && c <= 66977)
                  : c <= 66993)))
              : (c <= 67001 || (c < 67424
                ? (c < 67072
                  ? (c >= 67003 && c <= 67004)
                  : (c <= 67382 || (c >= 67392 && c <= 67413)))
                : (c <= 67431 || (c < 67463
                  ? (c >= 67456 && c <= 67461)
                  : c <= 67504)))))
            : (c <= 67514 || (c < 67680
              ? (c < 67639
                ? (c < 67592
                  ? (c >= 67584 && c <= 67589)
                  : (c <= 67592 || (c >= 67594 && c <= 67637)))
                : (c <= 67640 || (c < 67647
                  ? c == 67644
                  : c <= 67669)))
              : (c <= 67702 || (c < 67828
                ? (c < 67808
                  ? (c >= 67712 && c <= 67742)
                  : c <= 67826)
                : (c <= 67829 || (c < 67872
                  ? (c >= 67840 && c <= 67861)
                  : c <= 67883)))))))))))))));
}

static inline bool sym_identifier_character_set_4(int32_t c) {
  return (c < 6400
    ? (c < 2979
      ? (c < 2365
        ? (c < 1376
          ? (c < 750
            ? (c < 186
              ? (c < '_'
                ? (c < '0'
                  ? c == '$'
                  : (c <= '9' || (c >= 'A' && c <= 'Z')))
                : (c <= '_' || (c < 170
                  ? (c >= 'a' && c <= 'z')
                  : (c <= 170 || c == 181))))
              : (c <= 186 || (c < 710
                ? (c < 216
                  ? (c >= 192 && c <= 214)
                  : (c <= 246 || (c >= 248 && c <= 705)))
                : (c <= 721 || (c < 748
                  ? (c >= 736 && c <= 740)
                  : c <= 748)))))
            : (c <= 750 || (c < 908
              ? (c < 895
                ? (c < 886
                  ? (c >= 880 && c <= 884)
                  : (c <= 887 || (c >= 890 && c <= 893)))
                : (c <= 895 || (c < 904
                  ? c == 902
                  : c <= 906)))
              : (c <= 908 || (c < 1162
                ? (c < 931
                  ? (c >= 910 && c <= 929)
                  : (c <= 1013 || (c >= 1015 && c <= 1153)))
                : (c <= 1327 || (c < 1369
                  ? (c >= 1329 && c <= 1366)
                  : c <= 1369)))))))
          : (c <= 1416 || (c < 1969
            ? (c < 1749
              ? (c < 1632
                ? (c < 1519
                  ? (c >= 1488 && c <= 1514)
                  : (c <= 1522 || (c >= 1568 && c <= 1610)))
                : (c <= 1641 || (c < 1649
                  ? (c >= 1646 && c <= 1647)
                  : c <= 1747)))
              : (c <= 1749 || (c < 1808
                ? (c < 1774
                  ? (c >= 1765 && c <= 1766)
                  : (c <= 1788 || c == 1791))
                : (c <= 1808 || (c < 1869
                  ? (c >= 1810 && c <= 1839)
                  : c <= 1957)))))
            : (c <= 1969 || (c < 2088
              ? (c < 2048
                ? (c < 2036
                  ? (c >= 1984 && c <= 2026)
                  : (c <= 2037 || c == 2042))
                : (c <= 2069 || (c < 2084
                  ? c == 2074
                  : c <= 2084)))
              : (c <= 2088 || (c < 2185
                ? (c < 2144
                  ? (c >= 2112 && c <= 2136)
                  : (c <= 2154 || (c >= 2160 && c <= 2183)))
                : (c <= 2190 || (c < 2308
                  ? (c >= 2208 && c <= 2249)
                  : c <= 2361)))))))))
        : (c <= 2365 || (c < 2703
          ? (c < 2534
            ? (c < 2474
              ? (c < 2417
                ? (c < 2392
                  ? c == 2384
                  : (c <= 2401 || (c >= 2406 && c <= 2415)))
                : (c <= 2432 || (c < 2447
                  ? (c >= 2437 && c <= 2444)
                  : (c <= 2448 || (c >= 2451 && c <= 2472)))))
              : (c <= 2480 || (c < 2510
                ? (c < 2486
                  ? c == 2482
                  : (c <= 2489 || c == 2493))
                : (c <= 2510 || (c < 2527
                  ? (c >= 2524 && c <= 2525)
                  : c <= 2529)))))
            : (c <= 2545 || (c < 2613
              ? (c < 2579
                ? (c < 2565
                  ? c == 2556
                  : (c <= 2570 || (c >= 2575 && c <= 2576)))
                : (c <= 2600 || (c < 2610
                  ? (c >= 2602 && c <= 2608)
                  : c <= 2611)))
              : (c <= 2614 || (c < 2662
                ? (c < 2649
                  ? (c >= 2616 && c <= 2617)
                  : (c <= 2652 || c == 2654))
                : (c <= 2671 || (c < 2693
                  ? (c >= 2674 && c <= 2676)
                  : c <= 2701)))))))
          : (c <= 2705 || (c < 2866
            ? (c < 2784
              ? (c < 2741
                ? (c < 2730
                  ? (c >= 2707 && c <= 2728)
                  : (c <= 2736 || (c >= 2738 && c <= 2739)))
                : (c <= 2745 || (c < 2768
                  ? c == 2749
                  : c <= 2768)))
              : (c <= 2785 || (c < 2831
                ? (c < 2809
                  ? (c >= 2790 && c <= 2799)
                  : (c <= 2809 || (c >= 2821 && c <= 2828)))
                : (c <= 2832 || (c < 2858
                  ? (c >= 2835 && c <= 2856)
                  : c <= 2864)))))
            : (c <= 2867 || (c < 2947
              ? (c < 2911
                ? (c < 2877
                  ? (c >= 2869 && c <= 2873)
                  : (c <= 2877 || (c >= 2908 && c <= 2909)))
                : (c <= 2913 || (c < 2929
                  ? (c >= 2918 && c <= 2927)
                  : c <= 2929)))
              : (c <= 2947 || (c < 2969
                ? (c < 2958
                  ? (c >= 2949 && c <= 2954)
                  : (c <= 2960 || (c >= 2962 && c <= 2965)))
                : (c <= 2970 || (c < 2974
                  ? c == 2972
                  : c <= 2975)))))))))))
      : (c <= 2980 || (c < 3872
        ? (c < 3406
          ? (c < 3205
            ? (c < 3114
              ? (c < 3046
                ? (c < 2990
                  ? (c >= 2984 && c <= 2986)
                  : (c <= 3001 || c == 3024))
                : (c <= 3055 || (c < 3086
                  ? (c >= 3077 && c <= 3084)
                  : (c <= 3088 || (c >= 3090 && c <= 3112)))))
              : (c <= 3129 || (c < 3168
                ? (c < 3160
                  ? c == 3133
                  : (c <= 3162 || c == 3165))
                : (c <= 3169 || (c < 3200
                  ? (c >= 3174 && c <= 3183)
                  : c <= 3200)))))
            : (c <= 3212 || (c < 3296
              ? (c < 3253
                ? (c < 3218
                  ? (c >= 3214 && c <= 3216)
                  : (c <= 3240 || (c >= 3242 && c <= 3251)))
                : (c <= 3257 || (c < 3293
                  ? c == 3261
                  : c <= 3294)))
              : (c <= 3297 || (c < 3342
                ? (c < 3313
                  ? (c >= 3302 && c <= 3311)
                  : (c <= 3314 || (c >= 3332 && c <= 3340)))
                : (c <= 3344 || (c < 3389
                  ? (c >= 3346 && c <= 3386)
                  : c <= 3389)))))))
          : (c <= 3406 || (c < 3664
            ? (c < 3507
              ? (c < 3450
                ? (c < 3423
                  ? (c >= 3412 && c <= 3414)
                  : (c <= 3425 || (c >= 3430 && c <= 3439)))
                : (c <= 3455 || (c < 3482
                  ? (c >= 3461 && c <= 3478)
                  : c <= 3505)))
              : (c <= 3515 || (c < 3585
                ? (c < 3520
                  ? c == 3517
                  : (c <= 3526 || (c >= 3558 && c <= 3567)))
                : (c <= 3632 || (c < 3648
                  ? (c >= 3634 && c <= 3635)
                  : c <= 3654)))))
            : (c <= 3673 || (c < 3762
              ? (c < 3724
                ? (c < 3716
                  ? (c >= 3713 && c <= 3714)
                  : (c <= 3716 || (c >= 3718 && c <= 3722)))
                : (c <= 3747 || (c < 3751
                  ? c == 3749
                  : c <= 3760)))
              : (c <= 3763 || (c < 3792
                ? (c < 3776
                  ? c == 3773
                  : (c <= 3780 || c == 3782))
                : (c <= 3801 || (c < 3840
                  ? (c >= 3804 && c <= 3807)
                  : c <= 3840)))))))))
        : (c <= 3881 || (c < 4802
          ? (c < 4295
            ? (c < 4193
              ? (c < 4096
                ? (c < 3913
                  ? (c >= 3904 && c <= 3911)
                  : (c <= 3948 || (c >= 3976 && c <= 3980)))
                : (c <= 4138 || (c < 4176
                  ? (c >= 4159 && c <= 4169)
                  : (c <= 4181 || (c >= 4186 && c <= 4189)))))
              : (c <= 4193 || (c < 4238
                ? (c < 4206
                  ? (c >= 4197 && c <= 4198)
                  : (c <= 4208 || (c >= 4213 && c <= 4225)))
                : (c <= 4238 || (c < 4256
                  ? (c >= 4240 && c <= 4249)
                  : c <= 4293)))))
            : (c <= 4295 || (c < 4698
              ? (c < 4682
                ? (c < 4304
                  ? c == 4301
                  : (c <= 4346 || (c >= 4348 && c <= 4680)))
                : (c <= 4685 || (c < 4696
                  ? (c >= 4688 && c <= 4694)
                  : c <= 4696)))
              : (c <= 4701 || (c < 4786
                ? (c < 4746
                  ? (c >= 4704 && c <= 4744)
                  : (c <= 4749 || (c >= 4752 && c <= 4784)))
                : (c <= 4789 || (c < 4800
                  ? (c >= 4792 && c <= 4798)
                  : c <= 4800)))))))
          : (c <= 4805 || (c < 5919
            ? (c < 5112
              ? (c < 4888
                ? (c < 4824
                  ? (c >= 4808 && c <= 4822)
                  : (c <= 4880 || (c >= 4882 && c <= 4885)))
                : (c <= 4954 || (c < 5024
                  ? (c >= 4992 && c <= 5007)
                  : c <= 5109)))
              : (c <= 5117 || (c < 5792
                ? (c < 5743
                  ? (c >= 5121 && c <= 5740)
                  : (c <= 5759 || (c >= 5761 && c <= 5786)))
                : (c <= 5866 || (c < 5888
                  ? (c >= 5873 && c <= 5880)
                  : c <= 5905)))))
            : (c <= 5937 || (c < 6112
              ? (c < 6016
                ? (c < 5984
                  ? (c >= 5952 && c <= 5969)
                  : (c <= 5996 || (c >= 5998 && c <= 6000)))
                : (c <= 6067 || (c < 6108
                  ? c == 6103
                  : c <= 6108)))
              : (c <= 6121 || (c < 6279
                ? (c < 6176
                  ? (c >= 6160 && c <= 6169)
                  : (c <= 6264 || (c >= 6272 && c <= 6276)))
                : (c <= 6312 || (c < 6320
                  ? c == 6314
                  : c <= 6389)))))))))))))
    : (c <= 6430 || (c < 43216
      ? (c < 8490
        ? (c < 7968
          ? (c < 7086
            ? (c < 6784
              ? (c < 6576
                ? (c < 6512
                  ? (c >= 6470 && c <= 6509)
                  : (c <= 6516 || (c >= 6528 && c <= 6571)))
                : (c <= 6601 || (c < 6656
                  ? (c >= 6608 && c <= 6617)
                  : (c <= 6678 || (c >= 6688 && c <= 6740)))))
              : (c <= 6793 || (c < 6981
                ? (c < 6823
                  ? (c >= 6800 && c <= 6809)
                  : (c <= 6823 || (c >= 6917 && c <= 6963)))
                : (c <= 6988 || (c < 7043
                  ? (c >= 6992 && c <= 7001)
                  : c <= 7072)))))
            : (c <= 7141 || (c < 7401
              ? (c < 7296
                ? (c < 7232
                  ? (c >= 7168 && c <= 7203)
                  : (c <= 7241 || (c >= 7245 && c <= 7293)))
                : (c <= 7304 || (c < 7357
                  ? (c >= 7312 && c <= 7354)
                  : c <= 7359)))
              : (c <= 7404 || (c < 7424
                ? (c < 7413
                  ? (c >= 7406 && c <= 7411)
                  : (c <= 7414 || c == 7418))
                : (c <= 7615 || (c < 7960
                  ? (c >= 7680 && c <= 7957)
                  : c <= 7965)))))))
          : (c <= 8005 || (c < 8160
            ? (c < 8064
              ? (c < 8027
                ? (c < 8016
                  ? (c >= 8008 && c <= 8013)
                  : (c <= 8023 || c == 8025))
                : (c <= 8027 || (c < 8031
                  ? c == 8029
                  : c <= 8061)))
              : (c <= 8116 || (c < 8134
                ? (c < 8126
                  ? (c >= 8118 && c <= 8124)
                  : (c <= 8126 || (c >= 8130 && c <= 8132)))
                : (c <= 8140 || (c < 8150
                  ? (c >= 8144 && c <= 8147)
                  : c <= 8155)))))
            : (c <= 8172 || (c < 8455
              ? (c < 8319
                ? (c < 8182
                  ? (c >= 8178 && c <= 8180)
                  : (c <= 8188 || c == 8305))
                : (c <= 8319 || (c < 8450
                  ? (c >= 8336 && c <= 8348)
                  : c <= 8450)))
              : (c <= 8455 || (c < 8484
                ? (c < 8469
                  ? (c >= 8458 && c <= 8467)
                  : (c <= 8469 || (c >= 8473 && c <= 8477)))
                : (c <= 8484 || (c < 8488
                  ? c == 8486
                  : c <= 8488)))))))))
        : (c <= 8493 || (c < 12449
          ? (c < 11680
            ? (c < 11506
              ? (c < 8526
                ? (c < 8508
                  ? (c >= 8495 && c <= 8505)
                  : (c <= 8511 || (c >= 8517 && c <= 8521)))
                : (c <= 8526 || (c < 11264
                  ? (c >= 8579 && c <= 8580)
                  : (c <= 11492 || (c >= 11499 && c <= 11502)))))
              : (c <= 11507 || (c < 11568
                ? (c < 11559
                  ? (c >= 11520 && c <= 11557)
                  : (c <= 11559 || c == 11565))
                : (c <= 11623 || (c < 11648
                  ? c == 11631
                  : c <= 11670)))))
            : (c <= 11686 || (c < 11736
              ? (c < 11712
                ? (c < 11696
                  ? (c >= 11688 && c <= 11694)
                  : (c <= 11702 || (c >= 11704 && c <= 11710)))
                : (c <= 11718 || (c < 11728
                  ? (c >= 11720 && c <= 11726)
                  : c <= 11734)))
              : (c <= 11742 || (c < 12347
                ? (c < 12293
                  ? c == 11823
                  : (c <= 12294 || (c >= 12337 && c <= 12341)))
                : (c <= 12348 || (c < 12445
                  ? (c >= 12353 && c <= 12438)
                  : c <= 12447)))))))
          : (c <= 12538 || (c < 42623
            ? (c < 19903
              ? (c < 12704
                ? (c < 12549
                  ? (c >= 12540 && c <= 12543)
                  : (c <= 12591 || (c >= 12593 && c <= 12686)))
                : (c <= 12735 || (c < 13312
                  ? (c >= 12784 && c <= 12799)
                  : c <= 13312)))
              : (c <= 19903 || (c < 42240
                ? (c < 40959
                  ? c == 19968
                  : (c <= 42124 || (c >= 42192 && c <= 42237)))
                : (c <= 42508 || (c < 42560
                  ? (c >= 42512 && c <= 42539)
                  : c <= 42606)))))
            : (c <= 42653 || (c < 42965
              ? (c < 42891
                ? (c < 42775
                  ? (c >= 42656 && c <= 42725)
                  : (c <= 42783 || (c >= 42786 && c <= 42888)))
                : (c <= 42954 || (c < 42963
                  ? (c >= 42960 && c <= 42961)
                  : c <= 42963)))
              : (c <= 42969 || (c < 43020
                ? (c < 43011
                  ? (c >= 42994 && c <= 43009)
                  : (c <= 43013 || (c >= 43015 && c <= 43018)))
                : (c <= 43042 || (c < 43138
                  ? (c >= 43072 && c <= 43123)
                  : c <= 43187)))))))))))
      : (c <= 43225 || (c < 65296
        ? (c < 43808
          ? (c < 43616
            ? (c < 43471
              ? (c < 43264
                ? (c < 43259
                  ? (c >= 43250 && c <= 43255)
                  : (c <= 43259 || (c >= 43261 && c <= 43262)))
                : (c <= 43301 || (c < 43360
                  ? (c >= 43312 && c <= 43334)
                  : (c <= 43388 || (c >= 43396 && c <= 43442)))))
              : (c <= 43481 || (c < 43584
                ? (c < 43494
                  ? (c >= 43488 && c <= 43492)
                  : (c <= 43518 || (c >= 43520 && c <= 43560)))
                : (c <= 43586 || (c < 43600
                  ? (c >= 43588 && c <= 43595)
                  : c <= 43609)))))
            : (c <= 43638 || (c < 43714
              ? (c < 43701
                ? (c < 43646
                  ? c == 43642
                  : (c <= 43695 || c == 43697))
                : (c <= 43702 || (c < 43712
                  ? (c >= 43705 && c <= 43709)
                  : c <= 43712)))
              : (c <= 43714 || (c < 43777
                ? (c < 43744
                  ? (c >= 43739 && c <= 43741)
                  : (c <= 43754 || (c >= 43762 && c <= 43764)))
                : (c <= 43782 || (c < 43793
                  ? (c >= 43785 && c <= 43790)
                  : c <= 43798)))))))
          : (c <= 43814 || (c < 64285
            ? (c < 55203
              ? (c < 43888
                ? (c < 43824
                  ? (c >= 43816 && c <= 43822)
                  : (c <= 43866 || (c >= 43868 && c <= 43881)))
                : (c <= 44002 || (c < 44032
                  ? (c >= 44016 && c <= 44025)
                  : c <= 44032)))
              : (c <= 55203 || (c < 64112
                ? (c < 55243
                  ? (c >= 55216 && c <= 55238)
                  : (c <= 55291 || (c >= 63744 && c <= 64109)))
                : (c <= 64217 || (c < 64275
                  ? (c >= 64256 && c <= 64262)
                  : c <= 64279)))))
            : (c <= 64285 || (c < 64326
              ? (c < 64318
                ? (c < 64298
                  ? (c >= 64287 && c <= 64296)
                  : (c <= 64310 || (c >= 64312 && c <= 64316)))
                : (c <= 64318 || (c < 64323
                  ? (c >= 64320 && c <= 64321)
                  : c <= 64324)))
              : (c <= 64433 || (c < 65008
                ? (c < 64848
                  ? (c >= 64467 && c <= 64829)
                  : (c <= 64911 || (c >= 64914 && c <= 64967)))
                : (c <= 65019 || (c < 65142
                  ? (c >= 65136 && c <= 65140)
                  : c <= 65276)))))))))
        : (c <= 65305 || (c < 66816
          ? (c < 65664
            ? (c < 65498
              ? (c < 65474
                ? (c < 65345
                  ? (c >= 65313 && c <= 65338)
                  : (c <= 65370 || (c >= 65382 && c <= 65470)))
                : (c <= 65479 || (c < 65490
                  ? (c >= 65482 && c <= 65487)
                  : c <= 65495)))
              : (c <= 65500 || (c < 65596
                ? (c < 65549
                  ? (c >= 65536 && c <= 65547)
                  : (c <= 65574 || (c >= 65576 && c <= 65594)))
                : (c <= 65597 || (c < 65616
                  ? (c >= 65599 && c <= 65613)
                  : c <= 65629)))))
            : (c <= 65786 || (c < 66432
              ? (c < 66349
                ? (c < 66208
                  ? (c >= 66176 && c <= 66204)
                  : (c <= 66256 || (c >= 66304 && c <= 66335)))
                : (c <= 66368 || (c < 66384
                  ? (c >= 66370 && c <= 66377)
                  : c <= 66421)))
              : (c <= 66461 || (c < 66720
                ? (c < 66504
                  ? (c >= 66464 && c <= 66499)
                  : (c <= 66511 || (c >= 66560 && c <= 66717)))
                : (c <= 66729 || (c < 66776
                  ? (c >= 66736 && c <= 66771)
                  : c <= 66811)))))))
          : (c <= 66855 || (c < 67463
            ? (c < 66979
              ? (c < 66956
                ? (c < 66928
                  ? (c >= 66864 && c <= 66915)
                  : (c <= 66938 || (c >= 66940 && c <= 66954)))
                : (c <= 66962 || (c < 66967
                  ? (c >= 66964 && c <= 66965)
                  : c <= 66977)))
              : (c <= 66993 || (c < 67392
                ? (c < 67003
                  ? (c >= 66995 && c <= 67001)
                  : (c <= 67004 || (c >= 67072 && c <= 67382)))
                : (c <= 67413 || (c < 67456
                  ? (c >= 67424 && c <= 67431)
                  : c <= 67461)))))
            : (c <= 67504 || (c < 67647
              ? (c < 67594
                ? (c < 67584
                  ? (c >= 67506 && c <= 67514)
                  : (c <= 67589 || c == 67592))
                : (c <= 67637 || (c < 67644
                  ? (c >= 67639 && c <= 67640)
                  : c <= 67644)))
              : (c <= 67669 || (c < 67828
                ? (c < 67712
                  ? (c >= 67680 && c <= 67702)
                  : (c <= 67742 || (c >= 67808 && c <= 67826)))
                : (c <= 67829 || (c < 67872
                  ? (c >= 67840 && c <= 67861)
                  : c <= 67883)))))))))))))));
}

static bool ts_lex(TSLexer *lexer, TSStateId state) {
  START_LEXER();
  eof = lexer->eof(lexer);
  switch (state) {
    case 0:
      if (eof) ADVANCE(37);
      if (lookahead == '!') ADVANCE(10);
      if (lookahead == '"') ADVANCE(60);
      if (lookahead == '%') ADVANCE(91);
      if (lookahead == '&') ADVANCE(72);
      if (lookahead == '\'') ADVANCE(11);
      if (lookahead == '(') ADVANCE(70);
      if (lookahead == ')') ADVANCE(73);
      if (lookahead == '*') ADVANCE(87);
      if (lookahead == '+') ADVANCE(85);
      if (lookahead == ',') ADVANCE(100);
      if (lookahead == '-') ADVANCE(86);
      if (lookahead == '.') ADVANCE(99);
      if (lookahead == '/') ADVANCE(88);
      if (lookahead == '0') ADVANCE(39);
      if (lookahead == ':') ADVANCE(105);
      if (lookahead == ';') ADVANCE(102);
      if (lookahead == '<') ADVANCE(78);
      if (lookahead == '=') ADVANCE(74);
      if (lookahead == '>') ADVANCE(76);
      if (lookahead == '?') ADVANCE(101);
      if (lookahead == '@') ADVANCE(106);
      if (lookahead == '[') ADVANCE(95);
      if (lookahead == '\\') ADVANCE(18);
      if (lookahead == ']') ADVANCE(96);
      if (lookahead == '^') ADVANCE(90);
      if (lookahead == '{') ADVANCE(103);
      if (lookahead == '|') ADVANCE(89);
      if (lookahead == '}') ADVANCE(104);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') SKIP(35)
      if (('1' <= lookahead && lookahead <= '9')) ADVANCE(40);
      if (sym_identifier_character_set_1(lookahead)) ADVANCE(108);
      END_STATE();
    case 1:
      if (lookahead == '!') ADVANCE(10);
      if (lookahead == '%') ADVANCE(91);
      if (lookahead == '&') ADVANCE(72);
      if (lookahead == '(') ADVANCE(70);
      if (lookahead == ')') ADVANCE(73);
      if (lookahead == '*') ADVANCE(87);
      if (lookahead == '+') ADVANCE(85);
      if (lookahead == ',') ADVANCE(100);
      if (lookahead == '-') ADVANCE(86);
      if (lookahead == '.') ADVANCE(97);
      if (lookahead == '/') ADVANCE(88);
      if (lookahead == ':') ADVANCE(105);
      if (lookahead == ';') ADVANCE(102);
      if (lookahead == '<') ADVANCE(78);
      if (lookahead == '=') ADVANCE(74);
      if (lookahead == '>') ADVANCE(76);
      if (lookahead == '?') ADVANCE(101);
      if (lookahead == '@') ADVANCE(106);
      if (lookahead == '[') ADVANCE(95);
      if (lookahead == ']') ADVANCE(96);
      if (lookahead == '^') ADVANCE(90);
      if (lookahead == '|') ADVANCE(89);
      if (lookahead == '}') ADVANCE(104);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') SKIP(1)
      if (sym_identifier_character_set_2(lookahead)) ADVANCE(108);
      END_STATE();
    case 2:
      if (lookahead == '"') ADVANCE(60);
      if (lookahead == '/') ADVANCE(62);
      if (lookahead == '\\') ADVANCE(18);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') ADVANCE(65);
      if (lookahead != 0) ADVANCE(66);
      END_STATE();
    case 3:
      if (lookahead == '&') ADVANCE(71);
      if (lookahead == ')') ADVANCE(73);
      if (lookahead == ',') ADVANCE(100);
      if (lookahead == '.') ADVANCE(98);
      if (lookahead == '/') ADVANCE(5);
      if (lookahead == ';') ADVANCE(102);
      if (lookahead == '<') ADVANCE(77);
      if (lookahead == '>') ADVANCE(75);
      if (lookahead == '@') ADVANCE(106);
      if (lookahead == '[') ADVANCE(95);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') SKIP(3)
      if (sym_identifier_character_set_2(lookahead)) ADVANCE(108);
      END_STATE();
    case 4:
      if (lookahead == '\'') ADVANCE(59);
      if (lookahead == '\\') ADVANCE(34);
      if (lookahead != 0 &&
          lookahead != '\n') ADVANCE(4);
      END_STATE();
    case 5:
      if (lookahead == '*') ADVANCE(7);
      if (lookahead == '/') ADVANCE(109);
      END_STATE();
    case 6:
      if (lookahead == '*') ADVANCE(6);
      if (lookahead == '/') ADVANCE(110);
      if (lookahead != 0) ADVANCE(7);
      END_STATE();
    case 7:
      if (lookahead == '*') ADVANCE(6);
      if (lookahead != 0) ADVANCE(7);
      END_STATE();
    case 8:
      if (lookahead == '.') ADVANCE(107);
      END_STATE();
    case 9:
      if (lookahead == '.') ADVANCE(31);
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(43);
      END_STATE();
    case 10:
      if (lookahead == '=') ADVANCE(82);
      END_STATE();
    case 11:
      if (lookahead == '\\') ADVANCE(34);
      if (lookahead != 0 &&
          lookahead != '\n' &&
          lookahead != '\'') ADVANCE(4);
      END_STATE();
    case 12:
      if (lookahead == '_') ADVANCE(12);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(40);
      END_STATE();
    case 13:
      if (lookahead == '_') ADVANCE(13);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(50);
      END_STATE();
    case 14:
      if (lookahead == '_') ADVANCE(14);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(51);
      END_STATE();
    case 15:
      if (lookahead == '_') ADVANCE(16);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(42);
      if (('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(43);
      END_STATE();
    case 16:
      if (lookahead == '_') ADVANCE(16);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(56);
      END_STATE();
    case 17:
      if (lookahead == '_') ADVANCE(16);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(55);
      if (('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(54);
      END_STATE();
    case 18:
      if (lookahead == 'u') ADVANCE(19);
      if (lookahead == 'x') ADVANCE(33);
      if (('0' <= lookahead && lookahead <= '7')) ADVANCE(69);
      if (lookahead != 0) ADVANCE(67);
      END_STATE();
    case 19:
      if (lookahead == '{') ADVANCE(32);
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(28);
      END_STATE();
    case 20:
      if (lookahead == '}') ADVANCE(67);
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(20);
      END_STATE();
    case 21:
      if (lookahead == '+' ||
          lookahead == '-') ADVANCE(26);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(51);
      END_STATE();
    case 22:
      if (lookahead == '+' ||
          lookahead == '-') ADVANCE(27);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(56);
      END_STATE();
    case 23:
      if (lookahead == '0' ||
          lookahead == '1') ADVANCE(48);
      END_STATE();
    case 24:
      if (('0' <= lookahead && lookahead <= '7')) ADVANCE(46);
      END_STATE();
    case 25:
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(50);
      END_STATE();
    case 26:
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(51);
      END_STATE();
    case 27:
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(56);
      END_STATE();
    case 28:
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(33);
      END_STATE();
    case 29:
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(67);
      END_STATE();
    case 30:
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(43);
      END_STATE();
    case 31:
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(54);
      END_STATE();
    case 32:
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(20);
      END_STATE();
    case 33:
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(29);
      END_STATE();
    case 34:
      if (lookahead != 0) ADVANCE(4);
      END_STATE();
    case 35:
      if (eof) ADVANCE(37);
      if (lookahead == '!') ADVANCE(10);
      if (lookahead == '"') ADVANCE(60);
      if (lookahead == '%') ADVANCE(91);
      if (lookahead == '&') ADVANCE(72);
      if (lookahead == '\'') ADVANCE(11);
      if (lookahead == '(') ADVANCE(70);
      if (lookahead == ')') ADVANCE(73);
      if (lookahead == '*') ADVANCE(87);
      if (lookahead == '+') ADVANCE(85);
      if (lookahead == ',') ADVANCE(100);
      if (lookahead == '-') ADVANCE(86);
      if (lookahead == '.') ADVANCE(99);
      if (lookahead == '/') ADVANCE(88);
      if (lookahead == '0') ADVANCE(39);
      if (lookahead == ':') ADVANCE(105);
      if (lookahead == ';') ADVANCE(102);
      if (lookahead == '<') ADVANCE(78);
      if (lookahead == '=') ADVANCE(74);
      if (lookahead == '>') ADVANCE(76);
      if (lookahead == '?') ADVANCE(101);
      if (lookahead == '@') ADVANCE(106);
      if (lookahead == '[') ADVANCE(95);
      if (lookahead == ']') ADVANCE(96);
      if (lookahead == '^') ADVANCE(90);
      if (lookahead == '{') ADVANCE(103);
      if (lookahead == '|') ADVANCE(89);
      if (lookahead == '}') ADVANCE(104);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') SKIP(35)
      if (('1' <= lookahead && lookahead <= '9')) ADVANCE(40);
      if (sym_identifier_character_set_3(lookahead)) ADVANCE(108);
      END_STATE();
    case 36:
      if (eof) ADVANCE(37);
      if (lookahead == '"') ADVANCE(60);
      if (lookahead == '&') ADVANCE(71);
      if (lookahead == '\'') ADVANCE(11);
      if (lookahead == '(') ADVANCE(70);
      if (lookahead == ')') ADVANCE(73);
      if (lookahead == ',') ADVANCE(100);
      if (lookahead == '.') ADVANCE(25);
      if (lookahead == '/') ADVANCE(5);
      if (lookahead == '0') ADVANCE(39);
      if (lookahead == ';') ADVANCE(102);
      if (lookahead == '>') ADVANCE(75);
      if (lookahead == '?') ADVANCE(101);
      if (lookahead == '@') ADVANCE(106);
      if (lookahead == '[') ADVANCE(95);
      if (lookahead == '{') ADVANCE(103);
      if (lookahead == '}') ADVANCE(104);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') SKIP(36)
      if (('1' <= lookahead && lookahead <= '9')) ADVANCE(40);
      if (sym_identifier_character_set_2(lookahead)) ADVANCE(108);
      END_STATE();
    case 37:
      ACCEPT_TOKEN(ts_builtin_sym_end);
      END_STATE();
    case 38:
      ACCEPT_TOKEN(sym_decimal_integer_literal);
      END_STATE();
    case 39:
      ACCEPT_TOKEN(sym_decimal_integer_literal);
      if (lookahead == '.') ADVANCE(52);
      if (lookahead == 'B' ||
          lookahead == 'b') ADVANCE(23);
      if (lookahead == 'L' ||
          lookahead == 'l') ADVANCE(38);
      if (lookahead == 'O' ||
          lookahead == 'o') ADVANCE(24);
      if (lookahead == 'X' ||
          lookahead == 'x') ADVANCE(9);
      if (lookahead == '_') ADVANCE(12);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(21);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(21);
      if (('D' <= lookahead && lookahead <= 'F') ||
          ('d' <= lookahead && lookahead <= 'f')) ADVANCE(49);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(40);
      END_STATE();
    case 40:
      ACCEPT_TOKEN(sym_decimal_integer_literal);
      if (lookahead == '.') ADVANCE(52);
      if (lookahead == 'L' ||
          lookahead == 'l') ADVANCE(38);
      if (lookahead == '_') ADVANCE(12);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(21);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(21);
      if (('D' <= lookahead && lookahead <= 'F') ||
          ('d' <= lookahead && lookahead <= 'f')) ADVANCE(49);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(40);
      END_STATE();
    case 41:
      ACCEPT_TOKEN(sym_hex_integer_literal);
      END_STATE();
    case 42:
      ACCEPT_TOKEN(sym_hex_integer_literal);
      if (lookahead == '.') ADVANCE(58);
      if (lookahead == 'L' ||
          lookahead == 'l') ADVANCE(41);
      if (lookahead == '_') ADVANCE(15);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(44);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(22);
      if (('D' <= lookahead && lookahead <= 'F') ||
          ('d' <= lookahead && lookahead <= 'f')) ADVANCE(43);
      if (('A' <= lookahead && lookahead <= 'C') ||
          ('a' <= lookahead && lookahead <= 'c')) ADVANCE(43);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(42);
      END_STATE();
    case 43:
      ACCEPT_TOKEN(sym_hex_integer_literal);
      if (lookahead == '.') ADVANCE(58);
      if (lookahead == 'L' ||
          lookahead == 'l') ADVANCE(41);
      if (lookahead == '_') ADVANCE(30);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(44);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(22);
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(43);
      END_STATE();
    case 44:
      ACCEPT_TOKEN(sym_hex_integer_literal);
      if (lookahead == '+' ||
          lookahead == '-') ADVANCE(27);
      if (lookahead == '.') ADVANCE(58);
      if (lookahead == 'L' ||
          lookahead == 'l') ADVANCE(41);
      if (lookahead == '_') ADVANCE(30);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(44);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(22);
      if (('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(43);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(42);
      END_STATE();
    case 45:
      ACCEPT_TOKEN(sym_octal_integer_literal);
      END_STATE();
    case 46:
      ACCEPT_TOKEN(sym_octal_integer_literal);
      if (lookahead == 'L' ||
          lookahead == 'l') ADVANCE(45);
      if (lookahead == '_') ADVANCE(24);
      if (('0' <= lookahead && lookahead <= '7')) ADVANCE(46);
      END_STATE();
    case 47:
      ACCEPT_TOKEN(sym_binary_integer_literal);
      END_STATE();
    case 48:
      ACCEPT_TOKEN(sym_binary_integer_literal);
      if (lookahead == 'L' ||
          lookahead == 'l') ADVANCE(47);
      if (lookahead == '_') ADVANCE(23);
      if (lookahead == '0' ||
          lookahead == '1') ADVANCE(48);
      END_STATE();
    case 49:
      ACCEPT_TOKEN(sym_decimal_floating_point_literal);
      END_STATE();
    case 50:
      ACCEPT_TOKEN(sym_decimal_floating_point_literal);
      if (lookahead == '_') ADVANCE(13);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(21);
      if (('D' <= lookahead && lookahead <= 'F') ||
          ('d' <= lookahead && lookahead <= 'f')) ADVANCE(49);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(50);
      END_STATE();
    case 51:
      ACCEPT_TOKEN(sym_decimal_floating_point_literal);
      if (lookahead == '_') ADVANCE(14);
      if (lookahead == 'D' ||
          lookahead == 'F' ||
          lookahead == 'd' ||
          lookahead == 'f') ADVANCE(49);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(51);
      END_STATE();
    case 52:
      ACCEPT_TOKEN(sym_decimal_floating_point_literal);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(21);
      if (('D' <= lookahead && lookahead <= 'F') ||
          ('d' <= lookahead && lookahead <= 'f')) ADVANCE(49);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(50);
      END_STATE();
    case 53:
      ACCEPT_TOKEN(sym_hex_floating_point_literal);
      END_STATE();
    case 54:
      ACCEPT_TOKEN(sym_hex_floating_point_literal);
      if (lookahead == '_') ADVANCE(31);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(57);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(22);
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(54);
      END_STATE();
    case 55:
      ACCEPT_TOKEN(sym_hex_floating_point_literal);
      if (lookahead == '_') ADVANCE(17);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(57);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(22);
      if (('D' <= lookahead && lookahead <= 'F') ||
          ('d' <= lookahead && lookahead <= 'f')) ADVANCE(54);
      if (('A' <= lookahead && lookahead <= 'C') ||
          ('a' <= lookahead && lookahead <= 'c')) ADVANCE(54);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(55);
      END_STATE();
    case 56:
      ACCEPT_TOKEN(sym_hex_floating_point_literal);
      if (lookahead == '_') ADVANCE(16);
      if (lookahead == 'D' ||
          lookahead == 'F' ||
          lookahead == 'd' ||
          lookahead == 'f') ADVANCE(53);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(56);
      END_STATE();
    case 57:
      ACCEPT_TOKEN(sym_hex_floating_point_literal);
      if (lookahead == '+' ||
          lookahead == '-') ADVANCE(27);
      if (lookahead == '_') ADVANCE(31);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(57);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(22);
      if (('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(54);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(55);
      END_STATE();
    case 58:
      ACCEPT_TOKEN(sym_hex_floating_point_literal);
      if (lookahead == 'E' ||
          lookahead == 'e') ADVANCE(57);
      if (lookahead == 'P' ||
          lookahead == 'p') ADVANCE(22);
      if (('0' <= lookahead && lookahead <= '9') ||
          ('A' <= lookahead && lookahead <= 'F') ||
          ('a' <= lookahead && lookahead <= 'f')) ADVANCE(54);
      END_STATE();
    case 59:
      ACCEPT_TOKEN(sym_character_literal);
      END_STATE();
    case 60:
      ACCEPT_TOKEN(anon_sym_DQUOTE);
      END_STATE();
    case 61:
      ACCEPT_TOKEN(sym_string_fragment);
      if (lookahead == '\n') ADVANCE(66);
      if (lookahead != 0 &&
          lookahead != '"' &&
          lookahead != '\\') ADVANCE(61);
      END_STATE();
    case 62:
      ACCEPT_TOKEN(sym_string_fragment);
      if (lookahead == '*') ADVANCE(64);
      if (lookahead == '/') ADVANCE(61);
      if (lookahead != 0 &&
          lookahead != '"' &&
          lookahead != '\\') ADVANCE(66);
      END_STATE();
    case 63:
      ACCEPT_TOKEN(sym_string_fragment);
      if (lookahead == '*') ADVANCE(63);
      if (lookahead == '/') ADVANCE(66);
      if (lookahead != 0 &&
          lookahead != '"' &&
          lookahead != '\\') ADVANCE(64);
      END_STATE();
    case 64:
      ACCEPT_TOKEN(sym_string_fragment);
      if (lookahead == '*') ADVANCE(63);
      if (lookahead != 0 &&
          lookahead != '"' &&
          lookahead != '\\') ADVANCE(64);
      END_STATE();
    case 65:
      ACCEPT_TOKEN(sym_string_fragment);
      if (lookahead == '/') ADVANCE(62);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') ADVANCE(65);
      if (lookahead != 0 &&
          lookahead != '"' &&
          lookahead != '\\') ADVANCE(66);
      END_STATE();
    case 66:
      ACCEPT_TOKEN(sym_string_fragment);
      if (lookahead != 0 &&
          lookahead != '"' &&
          lookahead != '\\') ADVANCE(66);
      END_STATE();
    case 67:
      ACCEPT_TOKEN(sym_escape_sequence);
      END_STATE();
    case 68:
      ACCEPT_TOKEN(sym_escape_sequence);
      if (('0' <= lookahead && lookahead <= '7')) ADVANCE(67);
      END_STATE();
    case 69:
      ACCEPT_TOKEN(sym_escape_sequence);
      if (('0' <= lookahead && lookahead <= '7')) ADVANCE(68);
      END_STATE();
    case 70:
      ACCEPT_TOKEN(anon_sym_LPAREN);
      END_STATE();
    case 71:
      ACCEPT_TOKEN(anon_sym_AMP);
      END_STATE();
    case 72:
      ACCEPT_TOKEN(anon_sym_AMP);
      if (lookahead == '&') ADVANCE(83);
      END_STATE();
    case 73:
      ACCEPT_TOKEN(anon_sym_RPAREN);
      END_STATE();
    case 74:
      ACCEPT_TOKEN(anon_sym_EQ);
      if (lookahead == '=') ADVANCE(81);
      END_STATE();
    case 75:
      ACCEPT_TOKEN(anon_sym_GT);
      END_STATE();
    case 76:
      ACCEPT_TOKEN(anon_sym_GT);
      if (lookahead == '=') ADVANCE(79);
      if (lookahead == '>') ADVANCE(93);
      END_STATE();
    case 77:
      ACCEPT_TOKEN(anon_sym_LT);
      END_STATE();
    case 78:
      ACCEPT_TOKEN(anon_sym_LT);
      if (lookahead == '<') ADVANCE(92);
      if (lookahead == '=') ADVANCE(80);
      END_STATE();
    case 79:
      ACCEPT_TOKEN(anon_sym_GT_EQ);
      END_STATE();
    case 80:
      ACCEPT_TOKEN(anon_sym_LT_EQ);
      END_STATE();
    case 81:
      ACCEPT_TOKEN(anon_sym_EQ_EQ);
      END_STATE();
    case 82:
      ACCEPT_TOKEN(anon_sym_BANG_EQ);
      END_STATE();
    case 83:
      ACCEPT_TOKEN(anon_sym_AMP_AMP);
      END_STATE();
    case 84:
      ACCEPT_TOKEN(anon_sym_PIPE_PIPE);
      END_STATE();
    case 85:
      ACCEPT_TOKEN(anon_sym_PLUS);
      END_STATE();
    case 86:
      ACCEPT_TOKEN(anon_sym_DASH);
      END_STATE();
    case 87:
      ACCEPT_TOKEN(anon_sym_STAR);
      END_STATE();
    case 88:
      ACCEPT_TOKEN(anon_sym_SLASH);
      if (lookahead == '*') ADVANCE(7);
      if (lookahead == '/') ADVANCE(109);
      END_STATE();
    case 89:
      ACCEPT_TOKEN(anon_sym_PIPE);
      if (lookahead == '|') ADVANCE(84);
      END_STATE();
    case 90:
      ACCEPT_TOKEN(anon_sym_CARET);
      END_STATE();
    case 91:
      ACCEPT_TOKEN(anon_sym_PERCENT);
      END_STATE();
    case 92:
      ACCEPT_TOKEN(anon_sym_LT_LT);
      END_STATE();
    case 93:
      ACCEPT_TOKEN(anon_sym_GT_GT);
      if (lookahead == '>') ADVANCE(94);
      END_STATE();
    case 94:
      ACCEPT_TOKEN(anon_sym_GT_GT_GT);
      END_STATE();
    case 95:
      ACCEPT_TOKEN(anon_sym_LBRACK);
      END_STATE();
    case 96:
      ACCEPT_TOKEN(anon_sym_RBRACK);
      END_STATE();
    case 97:
      ACCEPT_TOKEN(anon_sym_DOT);
      END_STATE();
    case 98:
      ACCEPT_TOKEN(anon_sym_DOT);
      if (lookahead == '.') ADVANCE(8);
      END_STATE();
    case 99:
      ACCEPT_TOKEN(anon_sym_DOT);
      if (lookahead == '.') ADVANCE(8);
      if (('0' <= lookahead && lookahead <= '9')) ADVANCE(50);
      END_STATE();
    case 100:
      ACCEPT_TOKEN(anon_sym_COMMA);
      END_STATE();
    case 101:
      ACCEPT_TOKEN(anon_sym_QMARK);
      END_STATE();
    case 102:
      ACCEPT_TOKEN(anon_sym_SEMI);
      END_STATE();
    case 103:
      ACCEPT_TOKEN(anon_sym_LBRACE);
      END_STATE();
    case 104:
      ACCEPT_TOKEN(anon_sym_RBRACE);
      END_STATE();
    case 105:
      ACCEPT_TOKEN(anon_sym_COLON);
      END_STATE();
    case 106:
      ACCEPT_TOKEN(anon_sym_AT);
      END_STATE();
    case 107:
      ACCEPT_TOKEN(anon_sym_DOT_DOT_DOT);
      END_STATE();
    case 108:
      ACCEPT_TOKEN(sym_identifier);
      if (sym_identifier_character_set_4(lookahead)) ADVANCE(108);
      END_STATE();
    case 109:
      ACCEPT_TOKEN(sym_line_comment);
      if (lookahead != 0 &&
          lookahead != '\n') ADVANCE(109);
      END_STATE();
    case 110:
      ACCEPT_TOKEN(sym_block_comment);
      END_STATE();
    default:
      return false;
  }
}

static bool ts_lex_keywords(TSLexer *lexer, TSStateId state) {
  START_LEXER();
  eof = lexer->eof(lexer);
  switch (state) {
    case 0:
      if (lookahead == 'a') ADVANCE(1);
      if (lookahead == 'b') ADVANCE(2);
      if (lookahead == 'c') ADVANCE(3);
      if (lookahead == 'd') ADVANCE(4);
      if (lookahead == 'e') ADVANCE(5);
      if (lookahead == 'f') ADVANCE(6);
      if (lookahead == 'i') ADVANCE(7);
      if (lookahead == 'l') ADVANCE(8);
      if (lookahead == 'm') ADVANCE(9);
      if (lookahead == 'n') ADVANCE(10);
      if (lookahead == 'o') ADVANCE(11);
      if (lookahead == 'p') ADVANCE(12);
      if (lookahead == 'r') ADVANCE(13);
      if (lookahead == 's') ADVANCE(14);
      if (lookahead == 't') ADVANCE(15);
      if (lookahead == 'v') ADVANCE(16);
      if (lookahead == 'y') ADVANCE(17);
      if (lookahead == '\t' ||
          lookahead == '\n' ||
          lookahead == '\r' ||
          lookahead == ' ') SKIP(0)
      END_STATE();
    case 1:
      if (lookahead == 's') ADVANCE(18);
      END_STATE();
    case 2:
      if (lookahead == 'o') ADVANCE(19);
      if (lookahead == 'y') ADVANCE(20);
      END_STATE();
    case 3:
      if (lookahead == 'h') ADVANCE(21);
      if (lookahead == 'l') ADVANCE(22);
      END_STATE();
    case 4:
      if (lookahead == 'e') ADVANCE(23);
      if (lookahead == 'o') ADVANCE(24);
      END_STATE();
    case 5:
      if (lookahead == 'x') ADVANCE(25);
      END_STATE();
    case 6:
      if (lookahead == 'a') ADVANCE(26);
      if (lookahead == 'l') ADVANCE(27);
      END_STATE();
    case 7:
      if (lookahead == 'm') ADVANCE(28);
      if (lookahead == 'n') ADVANCE(29);
      END_STATE();
    case 8:
      if (lookahead == 'o') ADVANCE(30);
      END_STATE();
    case 9:
      if (lookahead == 'o') ADVANCE(31);
      END_STATE();
    case 10:
      if (lookahead == 'u') ADVANCE(32);
      END_STATE();
    case 11:
      if (lookahead == 'n') ADVANCE(33);
      if (lookahead == 'p') ADVANCE(34);
      if (lookahead == 'u') ADVANCE(35);
      END_STATE();
    case 12:
      if (lookahead == 'a') ADVANCE(36);
      END_STATE();
    case 13:
      if (lookahead == 'e') ADVANCE(37);
      END_STATE();
    case 14:
      if (lookahead == 'h') ADVANCE(38);
      if (lookahead == 't') ADVANCE(39);
      if (lookahead == 'u') ADVANCE(40);
      END_STATE();
    case 15:
      if (lookahead == 'h') ADVANCE(41);
      if (lookahead == 'r') ADVANCE(42);
      END_STATE();
    case 16:
      if (lookahead == 'o') ADVANCE(43);
      END_STATE();
    case 17:
      if (lookahead == 'i') ADVANCE(44);
      END_STATE();
    case 18:
      if (lookahead == 's') ADVANCE(45);
      END_STATE();
    case 19:
      if (lookahead == 'o') ADVANCE(46);
      END_STATE();
    case 20:
      if (lookahead == 't') ADVANCE(47);
      END_STATE();
    case 21:
      if (lookahead == 'a') ADVANCE(48);
      END_STATE();
    case 22:
      if (lookahead == 'a') ADVANCE(49);
      END_STATE();
    case 23:
      if (lookahead == 'f') ADVANCE(50);
      END_STATE();
    case 24:
      if (lookahead == 'u') ADVANCE(51);
      END_STATE();
    case 25:
      if (lookahead == 't') ADVANCE(52);
      END_STATE();
    case 26:
      if (lookahead == 'l') ADVANCE(53);
      END_STATE();
    case 27:
      if (lookahead == 'o') ADVANCE(54);
      END_STATE();
    case 28:
      if (lookahead == 'p') ADVANCE(55);
      END_STATE();
    case 29:
      ACCEPT_TOKEN(anon_sym_in);
      if (lookahead == 'o') ADVANCE(56);
      if (lookahead == 't') ADVANCE(57);
      END_STATE();
    case 30:
      if (lookahead == 'n') ADVANCE(58);
      END_STATE();
    case 31:
      if (lookahead == 'd') ADVANCE(59);
      END_STATE();
    case 32:
      if (lookahead == 'l') ADVANCE(60);
      END_STATE();
    case 33:
      if (lookahead == 'e') ADVANCE(61);
      END_STATE();
    case 34:
      if (lookahead == 'e') ADVANCE(62);
      END_STATE();
    case 35:
      if (lookahead == 't') ADVANCE(63);
      END_STATE();
    case 36:
      if (lookahead == 'c') ADVANCE(64);
      if (lookahead == 'r') ADVANCE(65);
      END_STATE();
    case 37:
      if (lookahead == 'c') ADVANCE(66);
      if (lookahead == 't') ADVANCE(67);
      END_STATE();
    case 38:
      if (lookahead == 'o') ADVANCE(68);
      END_STATE();
    case 39:
      if (lookahead == 'a') ADVANCE(69);
      END_STATE();
    case 40:
      if (lookahead == 'p') ADVANCE(70);
      END_STATE();
    case 41:
      if (lookahead == 'i') ADVANCE(71);
      if (lookahead == 'r') ADVANCE(72);
      END_STATE();
    case 42:
      if (lookahead == 'u') ADVANCE(73);
      END_STATE();
    case 43:
      if (lookahead == 'i') ADVANCE(74);
      END_STATE();
    case 44:
      if (lookahead == 'e') ADVANCE(75);
      END_STATE();
    case 45:
      if (lookahead == 'e') ADVANCE(76);
      END_STATE();
    case 46:
      if (lookahead == 'l') ADVANCE(77);
      END_STATE();
    case 47:
      if (lookahead == 'e') ADVANCE(78);
      END_STATE();
    case 48:
      if (lookahead == 'r') ADVANCE(79);
      END_STATE();
    case 49:
      if (lookahead == 's') ADVANCE(80);
      END_STATE();
    case 50:
      if (lookahead == 'a') ADVANCE(81);
      END_STATE();
    case 51:
      if (lookahead == 'b') ADVANCE(82);
      END_STATE();
    case 52:
      if (lookahead == 'e') ADVANCE(83);
      END_STATE();
    case 53:
      if (lookahead == 's') ADVANCE(84);
      END_STATE();
    case 54:
      if (lookahead == 'a') ADVANCE(85);
      END_STATE();
    case 55:
      if (lookahead == 'o') ADVANCE(86);
      END_STATE();
    case 56:
      if (lookahead == 'u') ADVANCE(87);
      END_STATE();
    case 57:
      ACCEPT_TOKEN(anon_sym_int);
      if (lookahead == 'e') ADVANCE(88);
      END_STATE();
    case 58:
      if (lookahead == 'g') ADVANCE(89);
      END_STATE();
    case 59:
      if (lookahead == 'u') ADVANCE(90);
      END_STATE();
    case 60:
      if (lookahead == 'l') ADVANCE(91);
      END_STATE();
    case 61:
      if (lookahead == 'w') ADVANCE(92);
      END_STATE();
    case 62:
      if (lookahead == 'n') ADVANCE(93);
      END_STATE();
    case 63:
      ACCEPT_TOKEN(anon_sym_out);
      END_STATE();
    case 64:
      if (lookahead == 'k') ADVANCE(94);
      END_STATE();
    case 65:
      if (lookahead == 'c') ADVANCE(95);
      END_STATE();
    case 66:
      if (lookahead == 'o') ADVANCE(96);
      END_STATE();
    case 67:
      if (lookahead == 'u') ADVANCE(97);
      END_STATE();
    case 68:
      if (lookahead == 'r') ADVANCE(98);
      END_STATE();
    case 69:
      if (lookahead == 't') ADVANCE(99);
      END_STATE();
    case 70:
      if (lookahead == 'e') ADVANCE(100);
      END_STATE();
    case 71:
      if (lookahead == 's') ADVANCE(101);
      END_STATE();
    case 72:
      if (lookahead == 'o') ADVANCE(102);
      END_STATE();
    case 73:
      if (lookahead == 'e') ADVANCE(103);
      END_STATE();
    case 74:
      if (lookahead == 'd') ADVANCE(104);
      END_STATE();
    case 75:
      if (lookahead == 'l') ADVANCE(105);
      END_STATE();
    case 76:
      if (lookahead == 'r') ADVANCE(106);
      END_STATE();
    case 77:
      if (lookahead == 'e') ADVANCE(107);
      END_STATE();
    case 78:
      ACCEPT_TOKEN(anon_sym_byte);
      END_STATE();
    case 79:
      ACCEPT_TOKEN(anon_sym_char);
      END_STATE();
    case 80:
      if (lookahead == 's') ADVANCE(108);
      END_STATE();
    case 81:
      if (lookahead == 'u') ADVANCE(109);
      END_STATE();
    case 82:
      if (lookahead == 'l') ADVANCE(110);
      END_STATE();
    case 83:
      if (lookahead == 'n') ADVANCE(111);
      END_STATE();
    case 84:
      if (lookahead == 'e') ADVANCE(112);
      END_STATE();
    case 85:
      if (lookahead == 't') ADVANCE(113);
      END_STATE();
    case 86:
      if (lookahead == 'r') ADVANCE(114);
      END_STATE();
    case 87:
      if (lookahead == 't') ADVANCE(115);
      END_STATE();
    case 88:
      if (lookahead == 'r') ADVANCE(116);
      END_STATE();
    case 89:
      ACCEPT_TOKEN(anon_sym_long);
      END_STATE();
    case 90:
      if (lookahead == 'l') ADVANCE(117);
      END_STATE();
    case 91:
      ACCEPT_TOKEN(sym_null_literal);
      END_STATE();
    case 92:
      if (lookahead == 'a') ADVANCE(118);
      END_STATE();
    case 93:
      ACCEPT_TOKEN(anon_sym_open);
      END_STATE();
    case 94:
      if (lookahead == 'a') ADVANCE(119);
      END_STATE();
    case 95:
      if (lookahead == 'e') ADVANCE(120);
      END_STATE();
    case 96:
      if (lookahead == 'r') ADVANCE(121);
      END_STATE();
    case 97:
      if (lookahead == 'r') ADVANCE(122);
      END_STATE();
    case 98:
      if (lookahead == 't') ADVANCE(123);
      END_STATE();
    case 99:
      if (lookahead == 'i') ADVANCE(124);
      END_STATE();
    case 100:
      if (lookahead == 'r') ADVANCE(125);
      END_STATE();
    case 101:
      ACCEPT_TOKEN(sym_this);
      END_STATE();
    case 102:
      if (lookahead == 'w') ADVANCE(126);
      END_STATE();
    case 103:
      ACCEPT_TOKEN(sym_true);
      END_STATE();
    case 104:
      ACCEPT_TOKEN(sym_void_type);
      END_STATE();
    case 105:
      if (lookahead == 'd') ADVANCE(127);
      END_STATE();
    case 106:
      if (lookahead == 't') ADVANCE(128);
      END_STATE();
    case 107:
      if (lookahead == 'a') ADVANCE(129);
      END_STATE();
    case 108:
      ACCEPT_TOKEN(anon_sym_class);
      END_STATE();
    case 109:
      if (lookahead == 'l') ADVANCE(130);
      END_STATE();
    case 110:
      if (lookahead == 'e') ADVANCE(131);
      END_STATE();
    case 111:
      if (lookahead == 'd') ADVANCE(132);
      END_STATE();
    case 112:
      ACCEPT_TOKEN(sym_false);
      END_STATE();
    case 113:
      ACCEPT_TOKEN(anon_sym_float);
      END_STATE();
    case 114:
      if (lookahead == 't') ADVANCE(133);
      END_STATE();
    case 115:
      ACCEPT_TOKEN(anon_sym_inout);
      END_STATE();
    case 116:
      if (lookahead == 'f') ADVANCE(134);
      END_STATE();
    case 117:
      if (lookahead == 'e') ADVANCE(135);
      END_STATE();
    case 118:
      if (lookahead == 'y') ADVANCE(136);
      END_STATE();
    case 119:
      if (lookahead == 'g') ADVANCE(137);
      END_STATE();
    case 120:
      if (lookahead == 'l') ADVANCE(138);
      END_STATE();
    case 121:
      if (lookahead == 'd') ADVANCE(139);
      END_STATE();
    case 122:
      if (lookahead == 'n') ADVANCE(140);
      END_STATE();
    case 123:
      ACCEPT_TOKEN(anon_sym_short);
      END_STATE();
    case 124:
      if (lookahead == 'c') ADVANCE(141);
      END_STATE();
    case 125:
      ACCEPT_TOKEN(sym_super);
      END_STATE();
    case 126:
      if (lookahead == 's') ADVANCE(142);
      END_STATE();
    case 127:
      ACCEPT_TOKEN(anon_sym_yield);
      END_STATE();
    case 128:
      ACCEPT_TOKEN(anon_sym_assert);
      END_STATE();
    case 129:
      if (lookahead == 'n') ADVANCE(143);
      END_STATE();
    case 130:
      if (lookahead == 't') ADVANCE(144);
      END_STATE();
    case 131:
      ACCEPT_TOKEN(anon_sym_double);
      END_STATE();
    case 132:
      if (lookahead == 's') ADVANCE(145);
      END_STATE();
    case 133:
      ACCEPT_TOKEN(anon_sym_import);
      END_STATE();
    case 134:
      if (lookahead == 'a') ADVANCE(146);
      END_STATE();
    case 135:
      ACCEPT_TOKEN(anon_sym_module);
      END_STATE();
    case 136:
      ACCEPT_TOKEN(anon_sym_oneway);
      END_STATE();
    case 137:
      if (lookahead == 'e') ADVANCE(147);
      END_STATE();
    case 138:
      if (lookahead == 'a') ADVANCE(148);
      END_STATE();
    case 139:
      ACCEPT_TOKEN(anon_sym_record);
      END_STATE();
    case 140:
      ACCEPT_TOKEN(anon_sym_return);
      END_STATE();
    case 141:
      ACCEPT_TOKEN(anon_sym_static);
      END_STATE();
    case 142:
      ACCEPT_TOKEN(anon_sym_throws);
      END_STATE();
    case 143:
      ACCEPT_TOKEN(sym_boolean_type);
      END_STATE();
    case 144:
      ACCEPT_TOKEN(anon_sym_default);
      END_STATE();
    case 145:
      ACCEPT_TOKEN(anon_sym_extends);
      END_STATE();
    case 146:
      if (lookahead == 'c') ADVANCE(149);
      END_STATE();
    case 147:
      ACCEPT_TOKEN(anon_sym_package);
      END_STATE();
    case 148:
      if (lookahead == 'b') ADVANCE(150);
      END_STATE();
    case 149:
      if (lookahead == 'e') ADVANCE(151);
      END_STATE();
    case 150:
      if (lookahead == 'l') ADVANCE(152);
      END_STATE();
    case 151:
      ACCEPT_TOKEN(anon_sym_interface);
      END_STATE();
    case 152:
      if (lookahead == 'e') ADVANCE(153);
      END_STATE();
    case 153:
      ACCEPT_TOKEN(anon_sym_parcelable);
      END_STATE();
    default:
      return false;
  }
}

static const TSLexMode ts_lex_modes[STATE_COUNT] = {
  [0] = {.lex_state = 0},
  [1] = {.lex_state = 36},
  [2] = {.lex_state = 36},
  [3] = {.lex_state = 36},
  [4] = {.lex_state = 36},
  [5] = {.lex_state = 36},
  [6] = {.lex_state = 36},
  [7] = {.lex_state = 36},
  [8] = {.lex_state = 36},
  [9] = {.lex_state = 36},
  [10] = {.lex_state = 36},
  [11] = {.lex_state = 36},
  [12] = {.lex_state = 36},
  [13] = {.lex_state = 36},
  [14] = {.lex_state = 36},
  [15] = {.lex_state = 36},
  [16] = {.lex_state = 36},
  [17] = {.lex_state = 36},
  [18] = {.lex_state = 36},
  [19] = {.lex_state = 36},
  [20] = {.lex_state = 36},
  [21] = {.lex_state = 36},
  [22] = {.lex_state = 36},
  [23] = {.lex_state = 36},
  [24] = {.lex_state = 36},
  [25] = {.lex_state = 36},
  [26] = {.lex_state = 36},
  [27] = {.lex_state = 36},
  [28] = {.lex_state = 36},
  [29] = {.lex_state = 36},
  [30] = {.lex_state = 36},
  [31] = {.lex_state = 36},
  [32] = {.lex_state = 36},
  [33] = {.lex_state = 36},
  [34] = {.lex_state = 36},
  [35] = {.lex_state = 36},
  [36] = {.lex_state = 36},
  [37] = {.lex_state = 36},
  [38] = {.lex_state = 36},
  [39] = {.lex_state = 36},
  [40] = {.lex_state = 36},
  [41] = {.lex_state = 36},
  [42] = {.lex_state = 36},
  [43] = {.lex_state = 36},
  [44] = {.lex_state = 36},
  [45] = {.lex_state = 36},
  [46] = {.lex_state = 36},
  [47] = {.lex_state = 36},
  [48] = {.lex_state = 36},
  [49] = {.lex_state = 36},
  [50] = {.lex_state = 36},
  [51] = {.lex_state = 36},
  [52] = {.lex_state = 36},
  [53] = {.lex_state = 36},
  [54] = {.lex_state = 36},
  [55] = {.lex_state = 36},
  [56] = {.lex_state = 36},
  [57] = {.lex_state = 36},
  [58] = {.lex_state = 36},
  [59] = {.lex_state = 36},
  [60] = {.lex_state = 36},
  [61] = {.lex_state = 36},
  [62] = {.lex_state = 1},
  [63] = {.lex_state = 0},
  [64] = {.lex_state = 0},
  [65] = {.lex_state = 0},
  [66] = {.lex_state = 0},
  [67] = {.lex_state = 0},
  [68] = {.lex_state = 0},
  [69] = {.lex_state = 0},
  [70] = {.lex_state = 0},
  [71] = {.lex_state = 1},
  [72] = {.lex_state = 1},
  [73] = {.lex_state = 1},
  [74] = {.lex_state = 1},
  [75] = {.lex_state = 0},
  [76] = {.lex_state = 1},
  [77] = {.lex_state = 1},
  [78] = {.lex_state = 1},
  [79] = {.lex_state = 1},
  [80] = {.lex_state = 1},
  [81] = {.lex_state = 1},
  [82] = {.lex_state = 1},
  [83] = {.lex_state = 36},
  [84] = {.lex_state = 0},
  [85] = {.lex_state = 0},
  [86] = {.lex_state = 1},
  [87] = {.lex_state = 0},
  [88] = {.lex_state = 0},
  [89] = {.lex_state = 0},
  [90] = {.lex_state = 0},
  [91] = {.lex_state = 0},
  [92] = {.lex_state = 0},
  [93] = {.lex_state = 0},
  [94] = {.lex_state = 0},
  [95] = {.lex_state = 0},
  [96] = {.lex_state = 0},
  [97] = {.lex_state = 0},
  [98] = {.lex_state = 1},
  [99] = {.lex_state = 0},
  [100] = {.lex_state = 0},
  [101] = {.lex_state = 1},
  [102] = {.lex_state = 1},
  [103] = {.lex_state = 0},
  [104] = {.lex_state = 0},
  [105] = {.lex_state = 0},
  [106] = {.lex_state = 0},
  [107] = {.lex_state = 0},
  [108] = {.lex_state = 0},
  [109] = {.lex_state = 0},
  [110] = {.lex_state = 0},
  [111] = {.lex_state = 0},
  [112] = {.lex_state = 0},
  [113] = {.lex_state = 0},
  [114] = {.lex_state = 0},
  [115] = {.lex_state = 0},
  [116] = {.lex_state = 0},
  [117] = {.lex_state = 0},
  [118] = {.lex_state = 0},
  [119] = {.lex_state = 0},
  [120] = {.lex_state = 0},
  [121] = {.lex_state = 0},
  [122] = {.lex_state = 0},
  [123] = {.lex_state = 0},
  [124] = {.lex_state = 0},
  [125] = {.lex_state = 0},
  [126] = {.lex_state = 3},
  [127] = {.lex_state = 0},
  [128] = {.lex_state = 3},
  [129] = {.lex_state = 0},
  [130] = {.lex_state = 0},
  [131] = {.lex_state = 0},
  [132] = {.lex_state = 1},
  [133] = {.lex_state = 0},
  [134] = {.lex_state = 1},
  [135] = {.lex_state = 0},
  [136] = {.lex_state = 0},
  [137] = {.lex_state = 0},
  [138] = {.lex_state = 0},
  [139] = {.lex_state = 0},
  [140] = {.lex_state = 0},
  [141] = {.lex_state = 0},
  [142] = {.lex_state = 0},
  [143] = {.lex_state = 0},
  [144] = {.lex_state = 0},
  [145] = {.lex_state = 0},
  [146] = {.lex_state = 3},
  [147] = {.lex_state = 0},
  [148] = {.lex_state = 0},
  [149] = {.lex_state = 3},
  [150] = {.lex_state = 0},
  [151] = {.lex_state = 0},
  [152] = {.lex_state = 0},
  [153] = {.lex_state = 0},
  [154] = {.lex_state = 0},
  [155] = {.lex_state = 3},
  [156] = {.lex_state = 3},
  [157] = {.lex_state = 0},
  [158] = {.lex_state = 3},
  [159] = {.lex_state = 3},
  [160] = {.lex_state = 1},
  [161] = {.lex_state = 3},
  [162] = {.lex_state = 3},
  [163] = {.lex_state = 0},
  [164] = {.lex_state = 0},
  [165] = {.lex_state = 0},
  [166] = {.lex_state = 3},
  [167] = {.lex_state = 3},
  [168] = {.lex_state = 3},
  [169] = {.lex_state = 0},
  [170] = {.lex_state = 3},
  [171] = {.lex_state = 3},
  [172] = {.lex_state = 0},
  [173] = {.lex_state = 3},
  [174] = {.lex_state = 3},
  [175] = {.lex_state = 3},
  [176] = {.lex_state = 3},
  [177] = {.lex_state = 0},
  [178] = {.lex_state = 3},
  [179] = {.lex_state = 0},
  [180] = {.lex_state = 36},
  [181] = {.lex_state = 36},
  [182] = {.lex_state = 0},
  [183] = {.lex_state = 0},
  [184] = {.lex_state = 0},
  [185] = {.lex_state = 0},
  [186] = {.lex_state = 1},
  [187] = {.lex_state = 1},
  [188] = {.lex_state = 0},
  [189] = {.lex_state = 1},
  [190] = {.lex_state = 0},
  [191] = {.lex_state = 0},
  [192] = {.lex_state = 1},
  [193] = {.lex_state = 1},
  [194] = {.lex_state = 0},
  [195] = {.lex_state = 0},
  [196] = {.lex_state = 0},
  [197] = {.lex_state = 0},
  [198] = {.lex_state = 0},
  [199] = {.lex_state = 0},
  [200] = {.lex_state = 0},
  [201] = {.lex_state = 0},
  [202] = {.lex_state = 0},
  [203] = {.lex_state = 0},
  [204] = {.lex_state = 0},
  [205] = {.lex_state = 0},
  [206] = {.lex_state = 36},
  [207] = {.lex_state = 36},
  [208] = {.lex_state = 0},
  [209] = {.lex_state = 0},
  [210] = {.lex_state = 0},
  [211] = {.lex_state = 0},
  [212] = {.lex_state = 0},
  [213] = {.lex_state = 36},
  [214] = {.lex_state = 0},
  [215] = {.lex_state = 0},
  [216] = {.lex_state = 0},
  [217] = {.lex_state = 0},
  [218] = {.lex_state = 0},
  [219] = {.lex_state = 2},
  [220] = {.lex_state = 36},
  [221] = {.lex_state = 36},
  [222] = {.lex_state = 0},
  [223] = {.lex_state = 36},
  [224] = {.lex_state = 36},
  [225] = {.lex_state = 36},
  [226] = {.lex_state = 0},
  [227] = {.lex_state = 0},
  [228] = {.lex_state = 0},
  [229] = {.lex_state = 2},
  [230] = {.lex_state = 0},
  [231] = {.lex_state = 2},
  [232] = {.lex_state = 36},
  [233] = {.lex_state = 36},
  [234] = {.lex_state = 0},
  [235] = {.lex_state = 0},
  [236] = {.lex_state = 36},
  [237] = {.lex_state = 0},
  [238] = {.lex_state = 0},
  [239] = {.lex_state = 0},
  [240] = {.lex_state = 0},
  [241] = {.lex_state = 0},
  [242] = {.lex_state = 0},
  [243] = {.lex_state = 0},
  [244] = {.lex_state = 0},
  [245] = {.lex_state = 0},
  [246] = {.lex_state = 0},
  [247] = {.lex_state = 0},
  [248] = {.lex_state = 0},
  [249] = {.lex_state = 0},
  [250] = {.lex_state = 0},
  [251] = {.lex_state = 0},
  [252] = {.lex_state = 0},
  [253] = {.lex_state = 0},
  [254] = {.lex_state = 0},
  [255] = {.lex_state = 0},
  [256] = {.lex_state = 0},
  [257] = {.lex_state = 0},
  [258] = {.lex_state = 0},
  [259] = {.lex_state = 0},
  [260] = {.lex_state = 36},
  [261] = {.lex_state = 0},
  [262] = {.lex_state = 36},
  [263] = {.lex_state = 36},
  [264] = {.lex_state = 0},
  [265] = {.lex_state = 0},
  [266] = {.lex_state = 0},
  [267] = {.lex_state = 0},
  [268] = {.lex_state = 0},
  [269] = {.lex_state = 36},
  [270] = {.lex_state = 0},
  [271] = {.lex_state = 0},
  [272] = {.lex_state = 36},
  [273] = {.lex_state = 0},
  [274] = {.lex_state = 0},
  [275] = {.lex_state = 0},
  [276] = {.lex_state = 36},
  [277] = {.lex_state = 0},
  [278] = {.lex_state = 1},
  [279] = {.lex_state = 36},
  [280] = {.lex_state = 0},
  [281] = {.lex_state = 0},
  [282] = {.lex_state = 0},
  [283] = {.lex_state = 1},
  [284] = {.lex_state = 0},
  [285] = {.lex_state = 36},
  [286] = {.lex_state = 0},
  [287] = {.lex_state = 36},
  [288] = {.lex_state = 1},
  [289] = {.lex_state = 0},
  [290] = {.lex_state = 1},
  [291] = {.lex_state = 36},
  [292] = {.lex_state = 0},
  [293] = {.lex_state = 0},
  [294] = {.lex_state = 0},
  [295] = {.lex_state = 36},
  [296] = {.lex_state = 0},
  [297] = {.lex_state = 0},
  [298] = {.lex_state = 0},
  [299] = {.lex_state = 0},
  [300] = {.lex_state = 0},
  [301] = {.lex_state = 0},
  [302] = {.lex_state = 0},
  [303] = {.lex_state = 1},
  [304] = {.lex_state = 0},
  [305] = {.lex_state = 0},
  [306] = {.lex_state = 0},
  [307] = {.lex_state = 1},
  [308] = {.lex_state = 0},
  [309] = {.lex_state = 0},
  [310] = {.lex_state = 0},
  [311] = {.lex_state = 1},
  [312] = {.lex_state = 0},
  [313] = {.lex_state = 0},
  [314] = {.lex_state = 0},
  [315] = {.lex_state = 0},
  [316] = {.lex_state = 0},
  [317] = {.lex_state = 0},
  [318] = {.lex_state = 0},
  [319] = {.lex_state = 0},
  [320] = {.lex_state = 0},
  [321] = {.lex_state = 0},
  [322] = {.lex_state = 0},
  [323] = {.lex_state = 0},
  [324] = {.lex_state = 1},
  [325] = {.lex_state = 0},
  [326] = {.lex_state = 0},
  [327] = {.lex_state = 0},
  [328] = {.lex_state = 0},
  [329] = {.lex_state = 0},
};

static const uint16_t ts_parse_table[LARGE_STATE_COUNT][SYMBOL_COUNT] = {
  [0] = {
    [ts_builtin_sym_end] = ACTIONS(1),
    [sym_identifier] = ACTIONS(1),
    [sym_decimal_integer_literal] = ACTIONS(1),
    [sym_hex_integer_literal] = ACTIONS(1),
    [sym_octal_integer_literal] = ACTIONS(1),
    [sym_binary_integer_literal] = ACTIONS(1),
    [sym_decimal_floating_point_literal] = ACTIONS(1),
    [sym_hex_floating_point_literal] = ACTIONS(1),
    [sym_true] = ACTIONS(1),
    [sym_false] = ACTIONS(1),
    [sym_character_literal] = ACTIONS(1),
    [anon_sym_DQUOTE] = ACTIONS(1),
    [sym_escape_sequence] = ACTIONS(1),
    [sym_null_literal] = ACTIONS(1),
    [anon_sym_LPAREN] = ACTIONS(1),
    [anon_sym_AMP] = ACTIONS(1),
    [anon_sym_RPAREN] = ACTIONS(1),
    [anon_sym_EQ] = ACTIONS(1),
    [anon_sym_GT] = ACTIONS(1),
    [anon_sym_LT] = ACTIONS(1),
    [anon_sym_GT_EQ] = ACTIONS(1),
    [anon_sym_LT_EQ] = ACTIONS(1),
    [anon_sym_EQ_EQ] = ACTIONS(1),
    [anon_sym_BANG_EQ] = ACTIONS(1),
    [anon_sym_AMP_AMP] = ACTIONS(1),
    [anon_sym_PIPE_PIPE] = ACTIONS(1),
    [anon_sym_PLUS] = ACTIONS(1),
    [anon_sym_DASH] = ACTIONS(1),
    [anon_sym_STAR] = ACTIONS(1),
    [anon_sym_SLASH] = ACTIONS(1),
    [anon_sym_PIPE] = ACTIONS(1),
    [anon_sym_CARET] = ACTIONS(1),
    [anon_sym_PERCENT] = ACTIONS(1),
    [anon_sym_LT_LT] = ACTIONS(1),
    [anon_sym_GT_GT] = ACTIONS(1),
    [anon_sym_GT_GT_GT] = ACTIONS(1),
    [anon_sym_LBRACK] = ACTIONS(1),
    [anon_sym_RBRACK] = ACTIONS(1),
    [anon_sym_DOT] = ACTIONS(1),
    [anon_sym_class] = ACTIONS(1),
    [anon_sym_COMMA] = ACTIONS(1),
    [anon_sym_QMARK] = ACTIONS(1),
    [anon_sym_extends] = ACTIONS(1),
    [anon_sym_SEMI] = ACTIONS(1),
    [anon_sym_LBRACE] = ACTIONS(1),
    [anon_sym_RBRACE] = ACTIONS(1),
    [anon_sym_assert] = ACTIONS(1),
    [anon_sym_COLON] = ACTIONS(1),
    [anon_sym_return] = ACTIONS(1),
    [anon_sym_yield] = ACTIONS(1),
    [anon_sym_AT] = ACTIONS(1),
    [anon_sym_package] = ACTIONS(1),
    [anon_sym_import] = ACTIONS(1),
    [anon_sym_static] = ACTIONS(1),
    [anon_sym_oneway] = ACTIONS(1),
    [anon_sym_in] = ACTIONS(1),
    [anon_sym_out] = ACTIONS(1),
    [anon_sym_inout] = ACTIONS(1),
    [anon_sym_default] = ACTIONS(1),
    [anon_sym_interface] = ACTIONS(1),
    [anon_sym_parcelable] = ACTIONS(1),
    [anon_sym_byte] = ACTIONS(1),
    [anon_sym_short] = ACTIONS(1),
    [anon_sym_int] = ACTIONS(1),
    [anon_sym_long] = ACTIONS(1),
    [anon_sym_char] = ACTIONS(1),
    [anon_sym_float] = ACTIONS(1),
    [anon_sym_double] = ACTIONS(1),
    [sym_boolean_type] = ACTIONS(1),
    [sym_void_type] = ACTIONS(1),
    [anon_sym_DOT_DOT_DOT] = ACTIONS(1),
    [anon_sym_throws] = ACTIONS(1),
    [anon_sym_open] = ACTIONS(1),
    [anon_sym_module] = ACTIONS(1),
    [anon_sym_record] = ACTIONS(1),
    [sym_this] = ACTIONS(1),
    [sym_super] = ACTIONS(1),
    [sym_line_comment] = ACTIONS(3),
    [sym_block_comment] = ACTIONS(3),
  },
  [1] = {
    [sym_program] = STATE(323),
    [sym__literal] = STATE(80),
    [sym_string_literal] = STATE(82),
    [sym_expression] = STATE(123),
    [sym_cast_expression] = STATE(100),
    [sym_assignment_expression] = STATE(100),
    [sym_binary_expression] = STATE(100),
    [sym_primary_expression] = STATE(79),
    [sym_parenthesized_expression] = STATE(80),
    [sym_class_literal] = STATE(80),
    [sym_field_access] = STATE(74),
    [sym_array_access] = STATE(74),
    [sym_statement] = STATE(3),
    [sym_block] = STATE(38),
    [sym_expression_statement] = STATE(38),
    [sym_assert_statement] = STATE(38),
    [sym_return_statement] = STATE(38),
    [sym_yield_statement] = STATE(38),
    [sym__annotation] = STATE(138),
    [sym_marker_annotation] = STATE(138),
    [sym_annotation] = STATE(138),
    [sym_declaration] = STATE(38),
    [sym_package_declaration] = STATE(51),
    [sym_import_declaration] = STATE(51),
    [sym_single_type_import] = STATE(44),
    [sym_type_import_on_demand] = STATE(44),
    [sym_single_static_import] = STATE(44),
    [sym_static_import_on_demand] = STATE(44),
    [sym_modifiers] = STATE(139),
    [sym_interface_declaration] = STATE(51),
    [sym_parcelable_declaration] = STATE(51),
    [sym__unannotated_type] = STATE(160),
    [sym_scoped_type_identifier] = STATE(189),
    [sym_generic_type] = STATE(192),
    [sym_array_type] = STATE(173),
    [sym_integral_type] = STATE(173),
    [sym_floating_point_type] = STATE(173),
    [sym_local_variable_declaration] = STATE(38),
    [aux_sym_program_repeat1] = STATE(3),
    [aux_sym_dimensions_expr_repeat1] = STATE(202),
    [aux_sym_modifiers_repeat1] = STATE(121),
    [ts_builtin_sym_end] = ACTIONS(5),
    [sym_identifier] = ACTIONS(7),
    [sym_decimal_integer_literal] = ACTIONS(9),
    [sym_hex_integer_literal] = ACTIONS(9),
    [sym_octal_integer_literal] = ACTIONS(11),
    [sym_binary_integer_literal] = ACTIONS(11),
    [sym_decimal_floating_point_literal] = ACTIONS(11),
    [sym_hex_floating_point_literal] = ACTIONS(9),
    [sym_true] = ACTIONS(9),
    [sym_false] = ACTIONS(9),
    [sym_character_literal] = ACTIONS(11),
    [anon_sym_DQUOTE] = ACTIONS(13),
    [sym_null_literal] = ACTIONS(9),
    [anon_sym_LPAREN] = ACTIONS(15),
    [anon_sym_SEMI] = ACTIONS(17),
    [anon_sym_LBRACE] = ACTIONS(19),
    [anon_sym_assert] = ACTIONS(21),
    [anon_sym_return] = ACTIONS(23),
    [anon_sym_yield] = ACTIONS(25),
    [anon_sym_AT] = ACTIONS(27),
    [anon_sym_package] = ACTIONS(29),
    [anon_sym_import] = ACTIONS(31),
    [anon_sym_oneway] = ACTIONS(33),
    [anon_sym_in] = ACTIONS(33),
    [anon_sym_out] = ACTIONS(33),
    [anon_sym_inout] = ACTIONS(33),
    [anon_sym_interface] = ACTIONS(35),
    [anon_sym_parcelable] = ACTIONS(37),
    [anon_sym_byte] = ACTIONS(39),
    [anon_sym_short] = ACTIONS(39),
    [anon_sym_int] = ACTIONS(39),
    [anon_sym_long] = ACTIONS(39),
    [anon_sym_char] = ACTIONS(39),
    [anon_sym_float] = ACTIONS(41),
    [anon_sym_double] = ACTIONS(41),
    [sym_boolean_type] = ACTIONS(43),
    [sym_void_type] = ACTIONS(43),
    [anon_sym_open] = ACTIONS(45),
    [anon_sym_module] = ACTIONS(45),
    [anon_sym_record] = ACTIONS(45),
    [sym_this] = ACTIONS(47),
    [sym_super] = ACTIONS(49),
    [sym_line_comment] = ACTIONS(3),
    [sym_block_comment] = ACTIONS(3),
  },
  [2] = {
    [sym__literal] = STATE(80),
    [sym_string_literal] = STATE(82),
    [sym_expression] = STATE(123),
    [sym_cast_expression] = STATE(100),
    [sym_assignment_expression] = STATE(100),
    [sym_binary_expression] = STATE(100),
    [sym_primary_expression] = STATE(79),
    [sym_parenthesized_expression] = STATE(80),
    [sym_class_literal] = STATE(80),
    [sym_field_access] = STATE(74),
    [sym_array_access] = STATE(74),
    [sym_statement] = STATE(2),
    [sym_block] = STATE(38),
    [sym_expression_statement] = STATE(38),
    [sym_assert_statement] = STATE(38),
    [sym_return_statement] = STATE(38),
    [sym_yield_statement] = STATE(38),
    [sym__annotation] = STATE(138),
    [sym_marker_annotation] = STATE(138),
    [sym_annotation] = STATE(138),
    [sym_declaration] = STATE(38),
    [sym_package_declaration] = STATE(51),
    [sym_import_declaration] = STATE(51),
    [sym_single_type_import] = STATE(44),
    [sym_type_import_on_demand] = STATE(44),
    [sym_single_static_import] = STATE(44),
    [sym_static_import_on_demand] = STATE(44),
    [sym_modifiers] = STATE(139),
    [sym_interface_declaration] = STATE(51),
    [sym_parcelable_declaration] = STATE(51),
    [sym__unannotated_type] = STATE(160),
    [sym_scoped_type_identifier] = STATE(189),
    [sym_generic_type] = STATE(192),
    [sym_array_type] = STATE(173),
    [sym_integral_type] = STATE(173),
    [sym_floating_point_type] = STATE(173),
    [sym_local_variable_declaration] = STATE(38),
    [aux_sym_program_repeat1] = STATE(2),
    [aux_sym_dimensions_expr_repeat1] = STATE(202),
    [aux_sym_modifiers_repeat1] = STATE(121),
    [ts_builtin_sym_end] = ACTIONS(51),
    [sym_identifier] = ACTIONS(53),
    [sym_decimal_integer_literal] = ACTIONS(56),
    [sym_hex_integer_literal] = ACTIONS(56),
    [sym_octal_integer_literal] = ACTIONS(59),
    [sym_binary_integer_literal] = ACTIONS(59),
    [sym_decimal_floating_point_literal] = ACTIONS(59),
    [sym_hex_floating_point_literal] = ACTIONS(56),
    [sym_true] = ACTIONS(56),
    [sym_false] = ACTIONS(56),
    [sym_character_literal] = ACTIONS(59),
    [anon_sym_DQUOTE] = ACTIONS(62),
    [sym_null_literal] = ACTIONS(56),
    [anon_sym_LPAREN] = ACTIONS(65),
    [anon_sym_SEMI] = ACTIONS(68),
    [anon_sym_LBRACE] = ACTIONS(71),
    [anon_sym_RBRACE] = ACTIONS(51),
    [anon_sym_assert] = ACTIONS(74),
    [anon_sym_return] = ACTIONS(77),
    [anon_sym_yield] = ACTIONS(80),
    [anon_sym_AT] = ACTIONS(83),
    [anon_sym_package] = ACTIONS(86),
    [anon_sym_import] = ACTIONS(89),
    [anon_sym_oneway] = ACTIONS(92),
    [anon_sym_in] = ACTIONS(92),
    [anon_sym_out] = ACTIONS(92),
    [anon_sym_inout] = ACTIONS(92),
    [anon_sym_interface] = ACTIONS(95),
    [anon_sym_parcelable] = ACTIONS(98),
    [anon_sym_byte] = ACTIONS(101),
    [anon_sym_short] = ACTIONS(101),
    [anon_sym_int] = ACTIONS(101),
    [anon_sym_long] = ACTIONS(101),
    [anon_sym_char] = ACTIONS(101),
    [anon_sym_float] = ACTIONS(104),
    [anon_sym_double] = ACTIONS(104),
    [sym_boolean_type] = ACTIONS(107),
    [sym_void_type] = ACTIONS(107),
    [anon_sym_open] = ACTIONS(110),
    [anon_sym_module] = ACTIONS(110),
    [anon_sym_record] = ACTIONS(110),
    [sym_this] = ACTIONS(113),
    [sym_super] = ACTIONS(116),
    [sym_line_comment] = ACTIONS(3),
    [sym_block_comment] = ACTIONS(3),
  },
  [3] = {
    [sym__literal] = STATE(80),
    [sym_string_literal] = STATE(82),
    [sym_expression] = STATE(123),
    [sym_cast_expression] = STATE(100),
    [sym_assignment_expression] = STATE(100),
    [sym_binary_expression] = STATE(100),
    [sym_primary_expression] = STATE(79),
    [sym_parenthesized_expression] = STATE(80),
    [sym_class_literal] = STATE(80),
    [sym_field_access] = STATE(74),
    [sym_array_access] = STATE(74),
    [sym_statement] = STATE(2),
    [sym_block] = STATE(38),
    [sym_expression_statement] = STATE(38),
    [sym_assert_statement] = STATE(38),
    [sym_return_statement] = STATE(38),
    [sym_yield_statement] = STATE(38),
    [sym__annotation] = STATE(138),
    [sym_marker_annotation] = STATE(138),
    [sym_annotation] = STATE(138),
    [sym_declaration] = STATE(38),
    [sym_package_declaration] = STATE(51),
    [sym_import_declaration] = STATE(51),
    [sym_single_type_import] = STATE(44),
    [sym_type_import_on_demand] = STATE(44),
    [sym_single_static_import] = STATE(44),
    [sym_static_import_on_demand] = STATE(44),
    [sym_modifiers] = STATE(139),
    [sym_interface_declaration] = STATE(51),
    [sym_parcelable_declaration] = STATE(51),
    [sym__unannotated_type] = STATE(160),
    [sym_scoped_type_identifier] = STATE(189),
    [sym_generic_type] = STATE(192),
    [sym_array_type] = STATE(173),
    [sym_integral_type] = STATE(173),
    [sym_floating_point_type] = STATE(173),
    [sym_local_variable_declaration] = STATE(38),
    [aux_sym_program_repeat1] = STATE(2),
    [aux_sym_dimensions_expr_repeat1] = STATE(202),
    [aux_sym_modifiers_repeat1] = STATE(121),
    [ts_builtin_sym_end] = ACTIONS(119),
    [sym_identifier] = ACTIONS(7),
    [sym_decimal_integer_literal] = ACTIONS(9),
    [sym_hex_integer_literal] = ACTIONS(9),
    [sym_octal_integer_literal] = ACTIONS(11),
    [sym_binary_integer_literal] = ACTIONS(11),
    [sym_decimal_floating_point_literal] = ACTIONS(11),
    [sym_hex_floating_point_literal] = ACTIONS(9),
    [sym_true] = ACTIONS(9),
    [sym_false] = ACTIONS(9),
    [sym_character_literal] = ACTIONS(11),
    [anon_sym_DQUOTE] = ACTIONS(13),
    [sym_null_literal] = ACTIONS(9),
    [anon_sym_LPAREN] = ACTIONS(15),
    [anon_sym_SEMI] = ACTIONS(17),
    [anon_sym_LBRACE] = ACTIONS(19),
    [anon_sym_assert] = ACTIONS(21),
    [anon_sym_return] = ACTIONS(23),
    [anon_sym_yield] = ACTIONS(25),
    [anon_sym_AT] = ACTIONS(27),
    [anon_sym_package] = ACTIONS(29),
    [anon_sym_import] = ACTIONS(31),
    [anon_sym_oneway] = ACTIONS(33),
    [anon_sym_in] = ACTIONS(33),
    [anon_sym_out] = ACTIONS(33),
    [anon_sym_inout] = ACTIONS(33),
    [anon_sym_interface] = ACTIONS(35),
    [anon_sym_parcelable] = ACTIONS(37),
    [anon_sym_byte] = ACTIONS(39),
    [anon_sym_short] = ACTIONS(39),
    [anon_sym_int] = ACTIONS(39),
    [anon_sym_long] = ACTIONS(39),
    [anon_sym_char] = ACTIONS(39),
    [anon_sym_float] = ACTIONS(41),
    [anon_sym_double] = ACTIONS(41),
    [sym_boolean_type] = ACTIONS(43),
    [sym_void_type] = ACTIONS(43),
    [anon_sym_open] = ACTIONS(45),
    [anon_sym_module] = ACTIONS(45),
    [anon_sym_record] = ACTIONS(45),
    [sym_this] = ACTIONS(47),
    [sym_super] = ACTIONS(49),
    [sym_line_comment] = ACTIONS(3),
    [sym_block_comment] = ACTIONS(3),
  },
  [4] = {
    [sym__literal] = STATE(80),
    [sym_string_literal] = STATE(82),
    [sym_expression] = STATE(123),
    [sym_cast_expression] = STATE(100),
    [sym_assignment_expression] = STATE(100),
    [sym_binary_expression] = STATE(100),
    [sym_primary_expression] = STATE(79),
    [sym_parenthesized_expression] = STATE(80),
    [sym_class_literal] = STATE(80),
    [sym_field_access] = STATE(74),
    [sym_array_access] = STATE(74),
    [sym_statement] = STATE(5),
    [sym_block] = STATE(38),
    [sym_expression_statement] = STATE(38),
    [sym_assert_statement] = STATE(38),
    [sym_return_statement] = STATE(38),
    [sym_yield_statement] = STATE(38),
    [sym__annotation] = STATE(138),
    [sym_marker_annotation] = STATE(138),
    [sym_annotation] = STATE(138),
    [sym_declaration] = STATE(38),
    [sym_package_declaration] = STATE(51),
    [sym_import_declaration] = STATE(51),
    [sym_single_type_import] = STATE(44),
    [sym_type_import_on_demand] = STATE(44),
    [sym_single_static_import] = STATE(44),
    [sym_static_import_on_demand] = STATE(44),
    [sym_modifiers] = STATE(139),
    [sym_interface_declaration] = STATE(51),
    [sym_parcelable_declaration] = STATE(51),
    [sym__unannotated_type] = STATE(160),
    [sym_scoped_type_identifier] = STATE(189),
    [sym_generic_type] = STATE(192),
    [sym_array_type] = STATE(173),
    [sym_integral_type] = STATE(173),
    [sym_floating_point_type] = STATE(173),
    [sym_local_variable_declaration] = STATE(38),
    [aux_sym_program_repeat1] = STATE(5),
    [aux_sym_dimensions_expr_repeat1] = STATE(202),
    [aux_sym_modifiers_repeat1] = STATE(121),
    [sym_identifier] = ACTIONS(7),
    [sym_decimal_integer_literal] = ACTIONS(9),
    [sym_hex_integer_literal] = ACTIONS(9),
    [sym_octal_integer_literal] = ACTIONS(11),
    [sym_binary_integer_literal] = ACTIONS(11),
    [sym_decimal_floating_point_literal] = ACTIONS(11),
    [sym_hex_floating_point_literal] = ACTIONS(9),
    [sym_true] = ACTIONS(9),
    [sym_false] = ACTIONS(9),
    [sym_character_literal] = ACTIONS(11),
    [anon_sym_DQUOTE] = ACTIONS(13),
    [sym_null_literal] = ACTIONS(9),
    [anon_sym_LPAREN] = ACTIONS(15),
    [anon_sym_SEMI] = ACTIONS(17),
    [anon_sym_LBRACE] = ACTIONS(19),
    [anon_sym_RBRACE] = ACTIONS(121),
    [anon_sym_assert] = ACTIONS(21),
    [anon_sym_return] = ACTIONS(23),
    [anon_sym_yield] = ACTIONS(25),
    [anon_sym_AT] = ACTIONS(27),
    [anon_sym_package] = ACTIONS(29),
    [anon_sym_import] = ACTIONS(31),
    [anon_sym_oneway] = ACTIONS(33),
    [anon_sym_in] = ACTIONS(33),
    [anon_sym_out] = ACTIONS(33),
    [anon_sym_inout] = ACTIONS(33),
    [anon_sym_interface] = ACTIONS(35),
    [anon_sym_parcelable] = ACTIONS(37),
    [anon_sym_byte] = ACTIONS(39),
    [anon_sym_short] = ACTIONS(39),
    [anon_sym_int] = ACTIONS(39),
    [anon_sym_long] = ACTIONS(39),
    [anon_sym_char] = ACTIONS(39),
    [anon_sym_float] = ACTIONS(41),
    [anon_sym_double] = ACTIONS(41),
    [sym_boolean_type] = ACTIONS(43),
    [sym_void_type] = ACTIONS(43),
    [anon_sym_open] = ACTIONS(45),
    [anon_sym_module] = ACTIONS(45),
    [anon_sym_record] = ACTIONS(45),
    [sym_this] = ACTIONS(47),
    [sym_super] = ACTIONS(49),
    [sym_line_comment] = ACTIONS(3),
    [sym_block_comment] = ACTIONS(3),
  },
  [5] = {
    [sym__literal] = STATE(80),
    [sym_string_literal] = STATE(82),
    [sym_expression] = STATE(123),
    [sym_cast_expression] = STATE(100),
    [sym_assignment_expression] = STATE(100),
    [sym_binary_expression] = STATE(100),
    [sym_primary_expression] = STATE(79),
    [sym_parenthesized_expression] = STATE(80),
    [sym_class_literal] = STATE(80),
    [sym_field_access] = STATE(74),
    [sym_array_access] = STATE(74),
    [sym_statement] = STATE(2),
    [sym_block] = STATE(38),
    [sym_expression_statement] = STATE(38),
    [sym_assert_statement] = STATE(38),
    [sym_return_statement] = STATE(38),
    [sym_yield_statement] = STATE(38),
    [sym__annotation] = STATE(138),
    [sym_marker_annotation] = STATE(138),
    [sym_annotation] = STATE(138),
    [sym_declaration] = STATE(38),
    [sym_package_declaration] = STATE(51),
    [sym_import_declaration] = STATE(51),
    [sym_single_type_import] = STATE(44),
    [sym_type_import_on_demand] = STATE(44),
    [sym_single_static_import] = STATE(44),
    [sym_static_import_on_demand] = STATE(44),
    [sym_modifiers] = STATE(139),
    [sym_interface_declaration] = STATE(51),
    [sym_parcelable_declaration] = STATE(51),
    [sym__unannotated_type] = STATE(160),
    [sym_scoped_type_identifier] = STATE(189),
    [sym_generic_type] = STATE(192),
    [sym_array_type] = STATE(173),
    [sym_integral_type] = STATE(173),
    [sym_floating_point_type] = STATE(173),
    [sym_local_variable_declaration] = STATE(38),
    [aux_sym_program_repeat1] = STATE(2),
    [aux_sym_dimensions_expr_repeat1] = STATE(202),
    [aux_sym_modifiers_repeat1] = STATE(121),
    [sym_identifier] = ACTIONS(7),
    [sym_decimal_integer_literal] = ACTIONS(9),
    [sym_hex_integer_literal] = ACTIONS(9),
    [sym_octal_integer_literal] = ACTIONS(11),
    [sym_binary_integer_literal] = ACTIONS(11),
    [sym_decimal_floating_point_literal] = ACTIONS(11),
    [sym_hex_floating_point_literal] = ACTIONS(9),
    [sym_true] = ACTIONS(9),
    [sym_false] = ACTIONS(9),
    [sym_character_literal] = ACTIONS(11),
    [anon_sym_DQUOTE] = ACTIONS(13),
    [sym_null_literal] = ACTIONS(9),
    [anon_sym_LPAREN] = ACTIONS(15),
    [anon_sym_SEMI] = ACTIONS(17),
    [anon_sym_LBRACE] = ACTIONS(19),
    [anon_sym_RBRACE] = ACTIONS(123),
    [anon_sym_assert] = ACTIONS(21),
    [anon_sym_return] = ACTIONS(23),
    [anon_sym_yield] = ACTIONS(25),
    [anon_sym_AT] = ACTIONS(27),
    [anon_sym_package] = ACTIONS(29),
    [anon_sym_import] = ACTIONS(31),
    [anon_sym_oneway] = ACTIONS(33),
    [anon_sym_in] = ACTIONS(33),
    [anon_sym_out] = ACTIONS(33),
    [anon_sym_inout] = ACTIONS(33),
    [anon_sym_interface] = ACTIONS(35),
    [anon_sym_parcelable] = ACTIONS(37),
    [anon_sym_byte] = ACTIONS(39),
    [anon_sym_short] = ACTIONS(39),
    [anon_sym_int] = ACTIONS(39),
    [anon_sym_long] = ACTIONS(39),
    [anon_sym_char] = ACTIONS(39),
    [anon_sym_float] = ACTIONS(41),
    [anon_sym_double] = ACTIONS(41),
    [sym_boolean_type] = ACTIONS(43),
    [sym_void_type] = ACTIONS(43),
    [anon_sym_open] = ACTIONS(45),
    [anon_sym_module] = ACTIONS(45),
    [anon_sym_record] = ACTIONS(45),
    [sym_this] = ACTIONS(47),
    [sym_super] = ACTIONS(49),
    [sym_line_comment] = ACTIONS(3),
    [sym_block_comment] = ACTIONS(3),
  },
};

static const uint16_t ts_small_parse_table[] = {
  [0] = 27,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(125), 1,
      anon_sym_COMMA,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    ACTIONS(129), 1,
      anon_sym_RBRACE,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(109), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(242), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [110] = 27,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    ACTIONS(131), 1,
      sym_identifier,
    ACTIONS(133), 1,
      anon_sym_RPAREN,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(109), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    STATE(254), 1,
      sym_element_value_pair,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(300), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [220] = 27,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    ACTIONS(131), 1,
      sym_identifier,
    ACTIONS(135), 1,
      anon_sym_RPAREN,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(109), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    STATE(238), 1,
      sym_element_value_pair,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(328), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [330] = 26,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    ACTIONS(137), 1,
      anon_sym_RBRACE,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(109), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(282), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [437] = 26,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    ACTIONS(139), 1,
      anon_sym_RBRACE,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(109), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(282), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [544] = 25,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(109), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(297), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [648] = 26,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(141), 1,
      sym_identifier,
    ACTIONS(143), 1,
      anon_sym_AT,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(129), 1,
      sym_expression,
    STATE(186), 1,
      sym__unannotated_type,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(206), 1,
      sym_annotated_type,
    STATE(243), 1,
      sym__type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    STATE(111), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [754] = 25,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(109), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(282), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [858] = 25,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(127), 1,
      anon_sym_LBRACE,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(114), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    STATE(297), 5,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      sym__element_value,
      sym_element_value_array_initializer,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [962] = 23,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    ACTIONS(145), 1,
      anon_sym_SEMI,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(130), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1056] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(95), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1147] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(96), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1238] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(85), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1329] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(89), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1420] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(99), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1511] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(90), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1602] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(91), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1693] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(94), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1784] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(124), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1875] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(88), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [1966] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(84), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [2057] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(93), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [2148] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(92), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [2239] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(122), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [2330] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(87), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [2421] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(127), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [2512] = 22,
    ACTIONS(7), 1,
      sym_identifier,
    ACTIONS(13), 1,
      anon_sym_DQUOTE,
    ACTIONS(15), 1,
      anon_sym_LPAREN,
    ACTIONS(47), 1,
      sym_this,
    ACTIONS(49), 1,
      sym_super,
    STATE(79), 1,
      sym_primary_expression,
    STATE(82), 1,
      sym_string_literal,
    STATE(115), 1,
      sym_expression,
    STATE(189), 1,
      sym_scoped_type_identifier,
    STATE(192), 1,
      sym_generic_type,
    STATE(193), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(74), 2,
      sym_field_access,
      sym_array_access,
    ACTIONS(45), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(80), 3,
      sym__literal,
      sym_parenthesized_expression,
      sym_class_literal,
    STATE(100), 3,
      sym_cast_expression,
      sym_assignment_expression,
      sym_binary_expression,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(11), 4,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
    ACTIONS(9), 6,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
  [2603] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(147), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(149), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [2655] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(151), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(153), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [2707] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(155), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(157), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [2759] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(159), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(161), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [2811] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(163), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(165), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [2863] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(167), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(169), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [2915] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(171), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(173), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [2967] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(175), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(177), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3019] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(179), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(181), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3071] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(183), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(185), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3123] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(187), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(189), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3175] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(191), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(193), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3227] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(195), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(197), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3279] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(199), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(201), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3331] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(203), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(205), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3383] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(207), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(209), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3435] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(211), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(213), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3487] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(215), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(217), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3539] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(219), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(221), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3591] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(223), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(225), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3643] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(227), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(229), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3695] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(231), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(233), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3747] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(235), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(237), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3799] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(239), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(241), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3851] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(243), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(245), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3903] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(247), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(249), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [3955] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(251), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(253), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [4007] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(255), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(257), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [4059] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(259), 11,
      ts_builtin_sym_end,
      sym_octal_integer_literal,
      sym_binary_integer_literal,
      sym_decimal_floating_point_literal,
      sym_character_literal,
      anon_sym_DQUOTE,
      anon_sym_LPAREN,
      anon_sym_SEMI,
      anon_sym_LBRACE,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(261), 32,
      sym_decimal_integer_literal,
      sym_hex_integer_literal,
      sym_hex_floating_point_literal,
      sym_true,
      sym_false,
      sym_null_literal,
      anon_sym_assert,
      anon_sym_return,
      anon_sym_yield,
      anon_sym_package,
      anon_sym_import,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_parcelable,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_super,
      sym_identifier,
  [4111] = 10,
    ACTIONS(269), 1,
      anon_sym_EQ,
    ACTIONS(271), 1,
      anon_sym_LT,
    ACTIONS(274), 1,
      anon_sym_LBRACK,
    ACTIONS(277), 1,
      anon_sym_DOT,
    ACTIONS(281), 1,
      anon_sym_AT,
    STATE(174), 1,
      sym_type_arguments,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(263), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    ACTIONS(265), 5,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(267), 19,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [4168] = 18,
    ACTIONS(283), 1,
      sym_identifier,
    ACTIONS(286), 1,
      anon_sym_LT,
    ACTIONS(289), 1,
      anon_sym_RBRACE,
    ACTIONS(291), 1,
      anon_sym_AT,
    STATE(118), 1,
      sym_type_parameters,
    STATE(135), 1,
      sym_modifiers,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(179), 1,
      sym__unannotated_type,
    STATE(314), 1,
      sym__method_header,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(300), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(303), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(63), 2,
      sym_method_declaration,
      aux_sym_interface_body_repeat1,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(294), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(297), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4239] = 18,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(308), 1,
      anon_sym_LT,
    ACTIONS(310), 1,
      anon_sym_RBRACE,
    STATE(118), 1,
      sym_type_parameters,
    STATE(135), 1,
      sym_modifiers,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(179), 1,
      sym__unannotated_type,
    STATE(314), 1,
      sym__method_header,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(65), 2,
      sym_method_declaration,
      aux_sym_interface_body_repeat1,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(312), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4310] = 18,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(308), 1,
      anon_sym_LT,
    ACTIONS(314), 1,
      anon_sym_RBRACE,
    STATE(118), 1,
      sym_type_parameters,
    STATE(135), 1,
      sym_modifiers,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(179), 1,
      sym__unannotated_type,
    STATE(314), 1,
      sym__method_header,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(63), 2,
      sym_method_declaration,
      aux_sym_interface_body_repeat1,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(312), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4381] = 18,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(316), 1,
      anon_sym_RPAREN,
    STATE(69), 1,
      sym_receiver_parameter,
    STATE(119), 1,
      aux_sym_dimensions_expr_repeat1,
    STATE(133), 1,
      aux_sym_modifiers_repeat1,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(151), 1,
      sym_modifiers,
    STATE(163), 1,
      sym__unannotated_type,
    STATE(168), 1,
      sym_generic_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(268), 2,
      sym_formal_parameter,
      sym_spread_parameter,
    STATE(157), 3,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(312), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4451] = 15,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(318), 1,
      anon_sym_RBRACE,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(150), 1,
      sym_modifiers,
    STATE(168), 1,
      sym_generic_type,
    STATE(169), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(70), 2,
      sym_field_declaration,
      aux_sym_parcelable_body_repeat1,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(312), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4513] = 15,
    ACTIONS(320), 1,
      sym_identifier,
    ACTIONS(323), 1,
      anon_sym_RBRACE,
    ACTIONS(325), 1,
      anon_sym_AT,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(150), 1,
      sym_modifiers,
    STATE(168), 1,
      sym_generic_type,
    STATE(169), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(334), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(337), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(68), 2,
      sym_field_declaration,
      aux_sym_parcelable_body_repeat1,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(328), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(331), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4575] = 15,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(340), 1,
      anon_sym_RPAREN,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(151), 1,
      sym_modifiers,
    STATE(168), 1,
      sym_generic_type,
    STATE(177), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(247), 2,
      sym_formal_parameter,
      sym_spread_parameter,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(312), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4637] = 15,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(342), 1,
      anon_sym_RBRACE,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(150), 1,
      sym_modifiers,
    STATE(168), 1,
      sym_generic_type,
    STATE(169), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(68), 2,
      sym_field_declaration,
      aux_sym_parcelable_body_repeat1,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(312), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4699] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(344), 7,
      anon_sym_AMP,
      anon_sym_EQ,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(346), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [4736] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(348), 7,
      anon_sym_AMP,
      anon_sym_EQ,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(350), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [4773] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(352), 7,
      anon_sym_AMP,
      anon_sym_EQ,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(354), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [4810] = 4,
    ACTIONS(269), 1,
      anon_sym_EQ,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(265), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(267), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [4849] = 14,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(151), 1,
      sym_modifiers,
    STATE(168), 1,
      sym_generic_type,
    STATE(177), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(294), 2,
      sym_formal_parameter,
      sym_spread_parameter,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(312), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(133), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [4908] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(356), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(358), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [4944] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(360), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(362), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [4980] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(364), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(366), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5016] = 5,
    ACTIONS(372), 1,
      anon_sym_LBRACK,
    ACTIONS(374), 1,
      anon_sym_DOT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(368), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(370), 19,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5056] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(265), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(267), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5092] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(376), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(378), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5128] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(380), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(382), 21,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_LBRACK,
      anon_sym_RBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5164] = 15,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(384), 1,
      anon_sym_GT,
    ACTIONS(386), 1,
      anon_sym_QMARK,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(236), 2,
      sym_wildcard,
      sym__type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(110), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [5223] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(388), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(390), 19,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5257] = 13,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(394), 8,
      anon_sym_RPAREN,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5311] = 9,
    ACTIONS(271), 1,
      anon_sym_LT,
    ACTIONS(274), 1,
      anon_sym_LBRACK,
    ACTIONS(277), 1,
      anon_sym_DOT,
    ACTIONS(281), 1,
      anon_sym_AT,
    ACTIONS(416), 1,
      anon_sym_EQ,
    STATE(174), 1,
      sym_type_arguments,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(265), 5,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(267), 14,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [5357] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(418), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(420), 19,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5391] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(422), 6,
      anon_sym_RPAREN,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5449] = 10,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(428), 2,
      anon_sym_AMP,
      anon_sym_PIPE,
    ACTIONS(394), 11,
      anon_sym_RPAREN,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_CARET,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5497] = 11,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(428), 2,
      anon_sym_AMP,
      anon_sym_PIPE,
    ACTIONS(394), 9,
      anon_sym_RPAREN,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_CARET,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5547] = 6,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(428), 5,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(394), 15,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_CARET,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5587] = 12,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(428), 1,
      anon_sym_PIPE,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(394), 9,
      anon_sym_RPAREN,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_CARET,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5639] = 13,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(428), 1,
      anon_sym_PIPE,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(394), 8,
      anon_sym_RPAREN,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5693] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(428), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(394), 19,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5727] = 5,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(428), 5,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(394), 17,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_CARET,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5765] = 14,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(394), 7,
      anon_sym_RPAREN,
      anon_sym_PIPE_PIPE,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5821] = 14,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(386), 1,
      anon_sym_QMARK,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(285), 2,
      sym_wildcard,
      sym__type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(110), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [5877] = 10,
    ACTIONS(269), 1,
      anon_sym_EQ,
    ACTIONS(271), 1,
      anon_sym_LT,
    ACTIONS(277), 1,
      anon_sym_DOT,
    ACTIONS(281), 1,
      anon_sym_AT,
    ACTIONS(430), 1,
      anon_sym_AMP,
    STATE(174), 1,
      sym_type_arguments,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(274), 2,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
    ACTIONS(265), 4,
      anon_sym_GT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(267), 13,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [5925] = 8,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(428), 4,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_PIPE,
    ACTIONS(394), 13,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_CARET,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [5969] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(368), 6,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_SLASH,
      anon_sym_PIPE,
      anon_sym_GT_GT,
    ACTIONS(370), 19,
      anon_sym_RPAREN,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
      anon_sym_AMP_AMP,
      anon_sym_PIPE_PIPE,
      anon_sym_PLUS,
      anon_sym_DASH,
      anon_sym_STAR,
      anon_sym_CARET,
      anon_sym_PERCENT,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
      anon_sym_RBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_COLON,
  [6003] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(435), 8,
      anon_sym_LPAREN,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_DOT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(433), 16,
      anon_sym_package,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [6036] = 6,
    ACTIONS(439), 1,
      anon_sym_LPAREN,
    ACTIONS(443), 1,
      anon_sym_DOT,
    STATE(120), 1,
      sym_annotation_argument_list,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(441), 5,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(437), 16,
      anon_sym_package,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [6075] = 13,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    STATE(270), 1,
      sym__type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(111), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6127] = 13,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    STATE(298), 1,
      sym__type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(111), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6179] = 13,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    STATE(287), 1,
      sym__type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(111), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6231] = 13,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    STATE(221), 1,
      sym__type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(111), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6283] = 13,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    STATE(286), 1,
      sym__type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(111), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6335] = 13,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(180), 1,
      sym__unannotated_type,
    STATE(206), 1,
      sym_annotated_type,
    STATE(262), 1,
      sym__type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(111), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6387] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(445), 3,
      anon_sym_RPAREN,
      anon_sym_COMMA,
      anon_sym_RBRACE,
  [6442] = 12,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(447), 1,
      anon_sym_QMARK,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(181), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(145), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6491] = 11,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(181), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(145), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6537] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(451), 5,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(449), 16,
      anon_sym_package,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [6567] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(455), 5,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(453), 16,
      anon_sym_package,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [6597] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
    ACTIONS(445), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [6651] = 16,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(457), 1,
      anon_sym_SEMI,
    ACTIONS(459), 1,
      anon_sym_COLON,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [6707] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(463), 5,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(461), 16,
      anon_sym_package,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [6737] = 11,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(183), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(145), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6783] = 11,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(182), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(117), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6829] = 11,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(190), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    STATE(145), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [6875] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(467), 5,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(465), 16,
      anon_sym_package,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [6905] = 5,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(471), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(131), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(469), 11,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [6938] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(473), 1,
      anon_sym_RBRACK,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [6991] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(475), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [7044] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(477), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [7097] = 6,
    ACTIONS(481), 1,
      anon_sym_LT,
    ACTIONS(483), 1,
      anon_sym_AT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(486), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(125), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(479), 10,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7132] = 7,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(128), 1,
      aux_sym_dimensions_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(491), 6,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_DOT_DOT_DOT,
    ACTIONS(489), 7,
      anon_sym_DOT,
      anon_sym_throws,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
  [7169] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(495), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [7222] = 7,
    ACTIONS(501), 1,
      anon_sym_LBRACK,
    ACTIONS(504), 1,
      anon_sym_AT,
    STATE(128), 1,
      aux_sym_dimensions_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(499), 6,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_DOT_DOT_DOT,
    ACTIONS(497), 7,
      anon_sym_DOT,
      anon_sym_throws,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
  [7259] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(507), 1,
      anon_sym_RPAREN,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [7312] = 15,
    ACTIONS(392), 1,
      anon_sym_AMP,
    ACTIONS(406), 1,
      anon_sym_SLASH,
    ACTIONS(408), 1,
      anon_sym_PIPE,
    ACTIONS(410), 1,
      anon_sym_CARET,
    ACTIONS(414), 1,
      anon_sym_GT_GT,
    ACTIONS(424), 1,
      anon_sym_AMP_AMP,
    ACTIONS(426), 1,
      anon_sym_PIPE_PIPE,
    ACTIONS(509), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(396), 2,
      anon_sym_GT,
      anon_sym_LT,
    ACTIONS(398), 2,
      anon_sym_GT_EQ,
      anon_sym_LT_EQ,
    ACTIONS(400), 2,
      anon_sym_EQ_EQ,
      anon_sym_BANG_EQ,
    ACTIONS(402), 2,
      anon_sym_PLUS,
      anon_sym_DASH,
    ACTIONS(404), 2,
      anon_sym_STAR,
      anon_sym_PERCENT,
    ACTIONS(412), 2,
      anon_sym_LT_LT,
      anon_sym_GT_GT_GT,
  [7365] = 5,
    ACTIONS(511), 1,
      anon_sym_AT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(514), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(131), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(479), 11,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7398] = 6,
    ACTIONS(517), 1,
      anon_sym_LPAREN,
    ACTIONS(519), 1,
      anon_sym_DOT,
    STATE(141), 1,
      sym_annotation_argument_list,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(441), 3,
      anon_sym_LT,
      anon_sym_QMARK,
      anon_sym_AT,
    ACTIONS(437), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7433] = 6,
    ACTIONS(143), 1,
      anon_sym_AT,
    ACTIONS(521), 1,
      anon_sym_LT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(523), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    STATE(125), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_modifiers_repeat1,
    ACTIONS(469), 10,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7468] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(435), 5,
      anon_sym_LPAREN,
      anon_sym_LT,
      anon_sym_DOT,
      anon_sym_QMARK,
      anon_sym_AT,
    ACTIONS(433), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7496] = 12,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(308), 1,
      anon_sym_LT,
    STATE(118), 1,
      sym_type_parameters,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(179), 1,
      sym__unannotated_type,
    STATE(320), 1,
      sym__method_header,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [7542] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(527), 3,
      anon_sym_LT,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(525), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7568] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(451), 3,
      anon_sym_LT,
      anon_sym_QMARK,
      anon_sym_AT,
    ACTIONS(449), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7594] = 4,
    ACTIONS(531), 1,
      anon_sym_AT,
    ACTIONS(534), 1,
      anon_sym_package,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(529), 15,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_interface,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7622] = 10,
    ACTIONS(306), 1,
      sym_identifier,
    ACTIONS(536), 1,
      anon_sym_interface,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(164), 1,
      sym__unannotated_type,
    STATE(168), 1,
      sym_generic_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [7662] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(463), 3,
      anon_sym_LT,
      anon_sym_QMARK,
      anon_sym_AT,
    ACTIONS(461), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7688] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(467), 3,
      anon_sym_LT,
      anon_sym_QMARK,
      anon_sym_AT,
    ACTIONS(465), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7714] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(455), 3,
      anon_sym_LT,
      anon_sym_QMARK,
      anon_sym_AT,
    ACTIONS(453), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7740] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(540), 3,
      anon_sym_LT,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(538), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7766] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(544), 2,
      anon_sym_RPAREN,
      anon_sym_AT,
    ACTIONS(542), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7791] = 5,
    ACTIONS(548), 1,
      anon_sym_QMARK,
    ACTIONS(550), 1,
      anon_sym_AT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(145), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(546), 10,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7820] = 6,
    ACTIONS(553), 1,
      anon_sym_LT,
    ACTIONS(555), 1,
      anon_sym_DOT,
    STATE(174), 1,
      sym_type_arguments,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(263), 5,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(281), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [7851] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(559), 2,
      anon_sym_RPAREN,
      anon_sym_AT,
    ACTIONS(557), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7876] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(563), 2,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(561), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [7901] = 6,
    ACTIONS(553), 1,
      anon_sym_LT,
    ACTIONS(569), 1,
      anon_sym_DOT,
    STATE(175), 1,
      sym_type_arguments,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(565), 5,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(567), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [7932] = 9,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(168), 1,
      sym_generic_type,
    STATE(172), 1,
      sym__unannotated_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [7969] = 9,
    ACTIONS(306), 1,
      sym_identifier,
    STATE(149), 1,
      sym_scoped_type_identifier,
    STATE(165), 1,
      sym__unannotated_type,
    STATE(168), 1,
      sym_generic_type,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(41), 2,
      anon_sym_float,
      anon_sym_double,
    ACTIONS(43), 2,
      sym_boolean_type,
      sym_void_type,
    STATE(173), 3,
      sym_array_type,
      sym_integral_type,
      sym_floating_point_type,
    ACTIONS(39), 5,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
  [8006] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(573), 2,
      anon_sym_RPAREN,
      anon_sym_AT,
    ACTIONS(571), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [8031] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(577), 2,
      anon_sym_RBRACE,
      anon_sym_AT,
    ACTIONS(575), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [8056] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(581), 2,
      anon_sym_RPAREN,
      anon_sym_AT,
    ACTIONS(579), 14,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [8081] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(583), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(585), 9,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8105] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(497), 7,
      anon_sym_DOT,
      anon_sym_throws,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(499), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8129] = 4,
    ACTIONS(531), 1,
      anon_sym_AT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(529), 4,
      anon_sym_oneway,
      anon_sym_in,
      anon_sym_out,
      anon_sym_inout,
    ACTIONS(587), 10,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [8155] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(590), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(592), 9,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8179] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(594), 7,
      anon_sym_DOT,
      anon_sym_throws,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(596), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8203] = 10,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(600), 1,
      anon_sym_DOT,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(265), 1,
      sym_variable_declarator,
    STATE(315), 1,
      sym__variable_declarator_list,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8241] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(602), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(604), 9,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8265] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(606), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(608), 9,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8289] = 11,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(610), 1,
      sym_identifier,
    ACTIONS(612), 1,
      anon_sym_DOT_DOT_DOT,
    ACTIONS(614), 1,
      sym_this,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(284), 1,
      sym_variable_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 3,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8329] = 9,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(265), 1,
      sym_variable_declarator,
    STATE(305), 1,
      sym__variable_declarator_list,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8364] = 9,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(616), 1,
      anon_sym_DOT_DOT_DOT,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(292), 1,
      sym_variable_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8399] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(618), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(620), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8422] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(622), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(624), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8445] = 4,
    ACTIONS(569), 1,
      anon_sym_DOT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(565), 5,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(567), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8470] = 9,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(265), 1,
      sym_variable_declarator,
    STATE(322), 1,
      sym__variable_declarator_list,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8505] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(626), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(628), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8528] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(630), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(632), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8551] = 9,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(265), 1,
      sym_variable_declarator,
    STATE(299), 1,
      sym__variable_declarator_list,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8586] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(565), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(567), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8609] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(634), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(636), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8632] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(638), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(640), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8655] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(642), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(644), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8678] = 9,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(612), 1,
      anon_sym_DOT_DOT_DOT,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(284), 1,
      sym_variable_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8713] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(646), 6,
      anon_sym_DOT,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
    ACTIONS(648), 8,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_LBRACK,
      anon_sym_COMMA,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_DOT_DOT_DOT,
  [8736] = 8,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(240), 1,
      sym__method_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(650), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8768] = 7,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(652), 5,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_COMMA,
      anon_sym_SEMI,
  [8798] = 7,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
    ACTIONS(654), 5,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_COMMA,
      anon_sym_SEMI,
  [8828] = 8,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(250), 1,
      sym__method_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(650), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8860] = 8,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    STATE(266), 1,
      sym__method_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(650), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8892] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(658), 2,
      anon_sym_LBRACE,
      anon_sym_AT,
    ACTIONS(656), 10,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [8913] = 3,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(662), 2,
      anon_sym_LBRACE,
      anon_sym_AT,
    ACTIONS(660), 10,
      anon_sym_byte,
      anon_sym_short,
      anon_sym_int,
      anon_sym_long,
      anon_sym_char,
      anon_sym_float,
      anon_sym_double,
      sym_boolean_type,
      sym_void_type,
      sym_identifier,
  [8934] = 8,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(600), 1,
      anon_sym_DOT,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(652), 2,
      anon_sym_AMP,
      anon_sym_RPAREN,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8964] = 8,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(666), 1,
      anon_sym_DOT,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(264), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(664), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [8994] = 7,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(264), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(664), 3,
      anon_sym_RPAREN,
      anon_sym_COMMA,
      anon_sym_SEMI,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9022] = 6,
    ACTIONS(553), 1,
      anon_sym_LT,
    ACTIONS(668), 1,
      anon_sym_DOT,
    STATE(175), 1,
      sym_type_arguments,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(565), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    ACTIONS(567), 4,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_AT,
  [9048] = 8,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(671), 1,
      sym_identifier,
    ACTIONS(673), 1,
      sym_this,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9077] = 7,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(273), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(675), 2,
      anon_sym_SEMI,
      anon_sym_throws,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9104] = 4,
    ACTIONS(668), 1,
      anon_sym_DOT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(565), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
    ACTIONS(567), 4,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_LBRACK,
      anon_sym_AT,
  [9124] = 7,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(493), 1,
      anon_sym_LBRACK,
    ACTIONS(600), 1,
      anon_sym_DOT,
    STATE(126), 1,
      aux_sym_dimensions_repeat1,
    STATE(167), 1,
      sym_dimensions,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(200), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9150] = 5,
    ACTIONS(548), 1,
      anon_sym_LBRACK,
    ACTIONS(677), 1,
      anon_sym_AT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(546), 2,
      anon_sym_package,
      sym_identifier,
    STATE(194), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9171] = 5,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(680), 1,
      sym_identifier,
    STATE(263), 1,
      sym_type_parameter,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(203), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9191] = 5,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(680), 1,
      sym_identifier,
    STATE(291), 1,
      sym_type_parameter,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(203), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9211] = 3,
    ACTIONS(684), 1,
      sym_super,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(682), 5,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
  [9226] = 4,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(686), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(204), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9243] = 4,
    ACTIONS(690), 1,
      anon_sym_static,
    STATE(283), 1,
      sym_scoped_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(688), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9260] = 4,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(692), 1,
      anon_sym_LBRACK,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(194), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9277] = 4,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(694), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(205), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9294] = 4,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(696), 1,
      anon_sym_package,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(194), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9311] = 4,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(698), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(194), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9328] = 4,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(700), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(194), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9345] = 4,
    ACTIONS(27), 1,
      anon_sym_AT,
    ACTIONS(702), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    STATE(194), 4,
      sym__annotation,
      sym_marker_annotation,
      sym_annotation,
      aux_sym_dimensions_expr_repeat1,
  [9362] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(652), 5,
      anon_sym_AMP,
      anon_sym_RPAREN,
      anon_sym_GT,
      anon_sym_COMMA,
      anon_sym_SEMI,
  [9374] = 4,
    STATE(279), 1,
      sym__wildcard_bounds,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(704), 2,
      anon_sym_GT,
      anon_sym_COMMA,
    ACTIONS(706), 2,
      anon_sym_extends,
      sym_super,
  [9390] = 3,
    STATE(281), 1,
      sym_variable_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9404] = 3,
    STATE(132), 1,
      sym_scoped_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(708), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9418] = 3,
    STATE(102), 1,
      sym_scoped_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(710), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9432] = 3,
    STATE(311), 1,
      sym_scoped_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(712), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9446] = 3,
    STATE(290), 1,
      sym_scoped_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(714), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9460] = 4,
    STATE(276), 1,
      sym__wildcard_bounds,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(706), 2,
      anon_sym_extends,
      sym_super,
    ACTIONS(716), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [9476] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(718), 5,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_this,
      sym_identifier,
  [9488] = 3,
    STATE(293), 1,
      sym_variable_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9502] = 3,
    STATE(274), 1,
      sym_variable_declarator,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(598), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9516] = 3,
    STATE(288), 1,
      sym_scoped_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(720), 4,
      anon_sym_open,
      anon_sym_module,
      anon_sym_record,
      sym_identifier,
  [9530] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(722), 4,
      anon_sym_LBRACK,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_throws,
  [9541] = 4,
    ACTIONS(724), 1,
      anon_sym_DQUOTE,
    STATE(219), 1,
      aux_sym_string_literal_repeat1,
    ACTIONS(726), 2,
      sym_string_fragment,
      sym_escape_sequence,
    ACTIONS(729), 2,
      sym_line_comment,
      sym_block_comment,
  [9556] = 4,
    ACTIONS(733), 1,
      anon_sym_extends,
    STATE(295), 1,
      sym_type_bound,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(731), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [9571] = 4,
    ACTIONS(735), 1,
      anon_sym_AMP,
    STATE(225), 1,
      aux_sym_type_bound_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(737), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [9586] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(739), 4,
      anon_sym_LBRACK,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_throws,
  [9597] = 4,
    ACTIONS(741), 1,
      anon_sym_AMP,
    STATE(223), 1,
      aux_sym_type_bound_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(744), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [9612] = 4,
    ACTIONS(733), 1,
      anon_sym_extends,
    STATE(272), 1,
      sym_type_bound,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(746), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [9627] = 4,
    ACTIONS(735), 1,
      anon_sym_AMP,
    STATE(223), 1,
      aux_sym_type_bound_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(748), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [9642] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(750), 4,
      anon_sym_LBRACK,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_throws,
  [9653] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(752), 4,
      anon_sym_LBRACK,
      anon_sym_SEMI,
      anon_sym_AT,
      anon_sym_throws,
  [9664] = 5,
    ACTIONS(308), 1,
      anon_sym_LT,
    ACTIONS(754), 1,
      anon_sym_LBRACE,
    STATE(49), 1,
      sym_interface_body,
    STATE(280), 1,
      sym_type_parameters,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9681] = 4,
    ACTIONS(756), 1,
      anon_sym_DQUOTE,
    STATE(219), 1,
      aux_sym_string_literal_repeat1,
    ACTIONS(729), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(758), 2,
      sym_string_fragment,
      sym_escape_sequence,
  [9696] = 5,
    ACTIONS(308), 1,
      anon_sym_LT,
    ACTIONS(754), 1,
      anon_sym_LBRACE,
    STATE(42), 1,
      sym_interface_body,
    STATE(275), 1,
      sym_type_parameters,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9713] = 4,
    ACTIONS(760), 1,
      anon_sym_DQUOTE,
    STATE(229), 1,
      aux_sym_string_literal_repeat1,
    ACTIONS(729), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(762), 2,
      sym_string_fragment,
      sym_escape_sequence,
  [9728] = 4,
    ACTIONS(764), 1,
      anon_sym_GT,
    ACTIONS(766), 1,
      anon_sym_COMMA,
    STATE(232), 1,
      aux_sym_type_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9742] = 4,
    ACTIONS(769), 1,
      anon_sym_GT,
    ACTIONS(771), 1,
      anon_sym_COMMA,
    STATE(232), 1,
      aux_sym_type_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9756] = 4,
    ACTIONS(773), 1,
      anon_sym_RPAREN,
    ACTIONS(775), 1,
      anon_sym_COMMA,
    STATE(241), 1,
      aux_sym_annotation_argument_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9770] = 4,
    ACTIONS(139), 1,
      anon_sym_RBRACE,
    ACTIONS(777), 1,
      anon_sym_COMMA,
    STATE(261), 1,
      aux_sym_element_value_array_initializer_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9784] = 4,
    ACTIONS(779), 1,
      anon_sym_GT,
    ACTIONS(781), 1,
      anon_sym_COMMA,
    STATE(269), 1,
      aux_sym_type_arguments_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9798] = 4,
    ACTIONS(783), 1,
      anon_sym_RPAREN,
    ACTIONS(785), 1,
      anon_sym_COMMA,
    STATE(239), 1,
      aux_sym_formal_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9812] = 4,
    ACTIONS(775), 1,
      anon_sym_COMMA,
    ACTIONS(787), 1,
      anon_sym_RPAREN,
    STATE(234), 1,
      aux_sym_annotation_argument_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9826] = 4,
    ACTIONS(789), 1,
      anon_sym_RPAREN,
    ACTIONS(791), 1,
      anon_sym_COMMA,
    STATE(239), 1,
      aux_sym_formal_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9840] = 4,
    ACTIONS(794), 1,
      anon_sym_SEMI,
    ACTIONS(796), 1,
      anon_sym_throws,
    STATE(321), 1,
      sym_throws,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9854] = 4,
    ACTIONS(798), 1,
      anon_sym_RPAREN,
    ACTIONS(800), 1,
      anon_sym_COMMA,
    STATE(241), 1,
      aux_sym_annotation_argument_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9868] = 4,
    ACTIONS(803), 1,
      anon_sym_COMMA,
    ACTIONS(805), 1,
      anon_sym_RBRACE,
    STATE(235), 1,
      aux_sym_element_value_array_initializer_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9882] = 4,
    ACTIONS(807), 1,
      anon_sym_AMP,
    ACTIONS(809), 1,
      anon_sym_RPAREN,
    STATE(271), 1,
      aux_sym_cast_expression_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9896] = 4,
    ACTIONS(811), 1,
      anon_sym_COMMA,
    ACTIONS(813), 1,
      anon_sym_SEMI,
    STATE(248), 1,
      aux_sym_type_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9910] = 4,
    ACTIONS(815), 1,
      anon_sym_COMMA,
    ACTIONS(817), 1,
      anon_sym_SEMI,
    STATE(267), 1,
      aux_sym__variable_declarator_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9924] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(819), 3,
      anon_sym_RPAREN,
      anon_sym_COMMA,
      anon_sym_RBRACE,
  [9934] = 4,
    ACTIONS(785), 1,
      anon_sym_COMMA,
    ACTIONS(821), 1,
      anon_sym_RPAREN,
    STATE(237), 1,
      aux_sym_formal_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9948] = 4,
    ACTIONS(823), 1,
      anon_sym_COMMA,
    ACTIONS(826), 1,
      anon_sym_SEMI,
    STATE(248), 1,
      aux_sym_type_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9962] = 4,
    ACTIONS(785), 1,
      anon_sym_COMMA,
    ACTIONS(821), 1,
      anon_sym_RPAREN,
    STATE(239), 1,
      aux_sym_formal_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9976] = 4,
    ACTIONS(796), 1,
      anon_sym_throws,
    ACTIONS(828), 1,
      anon_sym_SEMI,
    STATE(319), 1,
      sym_throws,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [9990] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(830), 3,
      anon_sym_RPAREN,
      anon_sym_COMMA,
      anon_sym_RBRACE,
  [10000] = 4,
    ACTIONS(775), 1,
      anon_sym_COMMA,
    ACTIONS(832), 1,
      anon_sym_RPAREN,
    STATE(241), 1,
      aux_sym_annotation_argument_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10014] = 4,
    ACTIONS(834), 1,
      anon_sym_AMP,
    ACTIONS(837), 1,
      anon_sym_RPAREN,
    STATE(253), 1,
      aux_sym_cast_expression_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10028] = 4,
    ACTIONS(775), 1,
      anon_sym_COMMA,
    ACTIONS(839), 1,
      anon_sym_RPAREN,
    STATE(252), 1,
      aux_sym_annotation_argument_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10042] = 4,
    ACTIONS(841), 1,
      sym_identifier,
    ACTIONS(843), 1,
      anon_sym_STAR,
    STATE(306), 1,
      sym_asterisk,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10056] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(845), 3,
      anon_sym_RPAREN,
      anon_sym_COMMA,
      anon_sym_RBRACE,
  [10066] = 4,
    ACTIONS(847), 1,
      anon_sym_SEMI,
    ACTIONS(849), 1,
      anon_sym_LBRACE,
    STATE(46), 1,
      sym_parcelable_body,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10080] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(851), 3,
      anon_sym_RPAREN,
      anon_sym_COMMA,
      anon_sym_RBRACE,
  [10090] = 4,
    ACTIONS(843), 1,
      anon_sym_STAR,
    ACTIONS(853), 1,
      sym_identifier,
    STATE(313), 1,
      sym_asterisk,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10104] = 4,
    ACTIONS(855), 1,
      anon_sym_GT,
    ACTIONS(857), 1,
      anon_sym_COMMA,
    STATE(260), 1,
      aux_sym_type_arguments_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10118] = 4,
    ACTIONS(860), 1,
      anon_sym_COMMA,
    ACTIONS(863), 1,
      anon_sym_RBRACE,
    STATE(261), 1,
      aux_sym_element_value_array_initializer_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10132] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(744), 3,
      anon_sym_AMP,
      anon_sym_GT,
      anon_sym_COMMA,
  [10142] = 4,
    ACTIONS(771), 1,
      anon_sym_COMMA,
    ACTIONS(865), 1,
      anon_sym_GT,
    STATE(233), 1,
      aux_sym_type_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10156] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(867), 3,
      anon_sym_RPAREN,
      anon_sym_COMMA,
      anon_sym_SEMI,
  [10166] = 4,
    ACTIONS(815), 1,
      anon_sym_COMMA,
    ACTIONS(869), 1,
      anon_sym_SEMI,
    STATE(245), 1,
      aux_sym__variable_declarator_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10180] = 4,
    ACTIONS(796), 1,
      anon_sym_throws,
    ACTIONS(871), 1,
      anon_sym_SEMI,
    STATE(310), 1,
      sym_throws,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10194] = 4,
    ACTIONS(873), 1,
      anon_sym_COMMA,
    ACTIONS(876), 1,
      anon_sym_SEMI,
    STATE(267), 1,
      aux_sym__variable_declarator_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10208] = 4,
    ACTIONS(340), 1,
      anon_sym_RPAREN,
    ACTIONS(785), 1,
      anon_sym_COMMA,
    STATE(249), 1,
      aux_sym_formal_parameters_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10222] = 4,
    ACTIONS(781), 1,
      anon_sym_COMMA,
    ACTIONS(878), 1,
      anon_sym_GT,
    STATE(260), 1,
      aux_sym_type_arguments_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10236] = 4,
    ACTIONS(811), 1,
      anon_sym_COMMA,
    ACTIONS(880), 1,
      anon_sym_SEMI,
    STATE(244), 1,
      aux_sym_type_list_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10250] = 4,
    ACTIONS(807), 1,
      anon_sym_AMP,
    ACTIONS(882), 1,
      anon_sym_RPAREN,
    STATE(253), 1,
      aux_sym_cast_expression_repeat1,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10264] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(884), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [10273] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(886), 2,
      anon_sym_SEMI,
      anon_sym_throws,
  [10282] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(888), 2,
      anon_sym_COMMA,
      anon_sym_SEMI,
  [10291] = 3,
    ACTIONS(754), 1,
      anon_sym_LBRACE,
    STATE(41), 1,
      sym_interface_body,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10302] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(704), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [10311] = 3,
    ACTIONS(890), 1,
      anon_sym_LPAREN,
    STATE(191), 1,
      sym_formal_parameters,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10322] = 3,
    ACTIONS(435), 1,
      anon_sym_DOT,
    ACTIONS(892), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10333] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(894), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [10342] = 3,
    ACTIONS(754), 1,
      anon_sym_LBRACE,
    STATE(33), 1,
      sym_interface_body,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10353] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(896), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [10362] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(863), 2,
      anon_sym_COMMA,
      anon_sym_RBRACE,
  [10371] = 3,
    ACTIONS(898), 1,
      anon_sym_DOT,
    ACTIONS(900), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10382] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(902), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [10391] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(855), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [10400] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(904), 2,
      anon_sym_AMP,
      anon_sym_RPAREN,
  [10409] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(906), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [10418] = 3,
    ACTIONS(443), 1,
      anon_sym_DOT,
    ACTIONS(908), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10429] = 3,
    ACTIONS(910), 1,
      sym_identifier,
    STATE(296), 1,
      sym_element_value_pair,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10440] = 3,
    ACTIONS(443), 1,
      anon_sym_DOT,
    ACTIONS(912), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10451] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(764), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [10460] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(914), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [10469] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(916), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [10478] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(789), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [10487] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(918), 2,
      anon_sym_GT,
      anon_sym_COMMA,
  [10496] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(798), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [10505] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(920), 2,
      anon_sym_RPAREN,
      anon_sym_COMMA,
  [10514] = 2,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
    ACTIONS(826), 2,
      anon_sym_COMMA,
      anon_sym_SEMI,
  [10523] = 2,
    ACTIONS(922), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10531] = 2,
    ACTIONS(839), 1,
      anon_sym_RPAREN,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10539] = 2,
    ACTIONS(924), 1,
      anon_sym_RBRACK,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10547] = 2,
    ACTIONS(926), 1,
      sym_this,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10555] = 2,
    ACTIONS(928), 1,
      anon_sym_DOT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10563] = 2,
    ACTIONS(930), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10571] = 2,
    ACTIONS(932), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10579] = 2,
    ACTIONS(934), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10587] = 2,
    ACTIONS(936), 1,
      anon_sym_DOT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10595] = 2,
    ACTIONS(938), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10603] = 2,
    ACTIONS(940), 1,
      sym_this,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10611] = 2,
    ACTIONS(942), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10619] = 2,
    ACTIONS(944), 1,
      anon_sym_DOT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10627] = 2,
    ACTIONS(853), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10635] = 2,
    ACTIONS(946), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10643] = 2,
    ACTIONS(948), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10651] = 2,
    ACTIONS(950), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10659] = 2,
    ACTIONS(952), 1,
      anon_sym_class,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10667] = 2,
    ACTIONS(954), 1,
      anon_sym_RBRACK,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10675] = 2,
    ACTIONS(956), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10683] = 2,
    ACTIONS(958), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10691] = 2,
    ACTIONS(960), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10699] = 2,
    ACTIONS(962), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10707] = 2,
    ACTIONS(964), 1,
      anon_sym_SEMI,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10715] = 2,
    ACTIONS(966), 1,
      ts_builtin_sym_end,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10723] = 2,
    ACTIONS(374), 1,
      anon_sym_DOT,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10731] = 2,
    ACTIONS(968), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10739] = 2,
    ACTIONS(970), 1,
      sym_identifier,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10747] = 2,
    ACTIONS(805), 1,
      anon_sym_RBRACE,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10755] = 2,
    ACTIONS(787), 1,
      anon_sym_RPAREN,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
  [10763] = 2,
    ACTIONS(972), 1,
      anon_sym_EQ,
    ACTIONS(3), 2,
      sym_line_comment,
      sym_block_comment,
};

static const uint32_t ts_small_parse_table_map[] = {
  [SMALL_STATE(6)] = 0,
  [SMALL_STATE(7)] = 110,
  [SMALL_STATE(8)] = 220,
  [SMALL_STATE(9)] = 330,
  [SMALL_STATE(10)] = 437,
  [SMALL_STATE(11)] = 544,
  [SMALL_STATE(12)] = 648,
  [SMALL_STATE(13)] = 754,
  [SMALL_STATE(14)] = 858,
  [SMALL_STATE(15)] = 962,
  [SMALL_STATE(16)] = 1056,
  [SMALL_STATE(17)] = 1147,
  [SMALL_STATE(18)] = 1238,
  [SMALL_STATE(19)] = 1329,
  [SMALL_STATE(20)] = 1420,
  [SMALL_STATE(21)] = 1511,
  [SMALL_STATE(22)] = 1602,
  [SMALL_STATE(23)] = 1693,
  [SMALL_STATE(24)] = 1784,
  [SMALL_STATE(25)] = 1875,
  [SMALL_STATE(26)] = 1966,
  [SMALL_STATE(27)] = 2057,
  [SMALL_STATE(28)] = 2148,
  [SMALL_STATE(29)] = 2239,
  [SMALL_STATE(30)] = 2330,
  [SMALL_STATE(31)] = 2421,
  [SMALL_STATE(32)] = 2512,
  [SMALL_STATE(33)] = 2603,
  [SMALL_STATE(34)] = 2655,
  [SMALL_STATE(35)] = 2707,
  [SMALL_STATE(36)] = 2759,
  [SMALL_STATE(37)] = 2811,
  [SMALL_STATE(38)] = 2863,
  [SMALL_STATE(39)] = 2915,
  [SMALL_STATE(40)] = 2967,
  [SMALL_STATE(41)] = 3019,
  [SMALL_STATE(42)] = 3071,
  [SMALL_STATE(43)] = 3123,
  [SMALL_STATE(44)] = 3175,
  [SMALL_STATE(45)] = 3227,
  [SMALL_STATE(46)] = 3279,
  [SMALL_STATE(47)] = 3331,
  [SMALL_STATE(48)] = 3383,
  [SMALL_STATE(49)] = 3435,
  [SMALL_STATE(50)] = 3487,
  [SMALL_STATE(51)] = 3539,
  [SMALL_STATE(52)] = 3591,
  [SMALL_STATE(53)] = 3643,
  [SMALL_STATE(54)] = 3695,
  [SMALL_STATE(55)] = 3747,
  [SMALL_STATE(56)] = 3799,
  [SMALL_STATE(57)] = 3851,
  [SMALL_STATE(58)] = 3903,
  [SMALL_STATE(59)] = 3955,
  [SMALL_STATE(60)] = 4007,
  [SMALL_STATE(61)] = 4059,
  [SMALL_STATE(62)] = 4111,
  [SMALL_STATE(63)] = 4168,
  [SMALL_STATE(64)] = 4239,
  [SMALL_STATE(65)] = 4310,
  [SMALL_STATE(66)] = 4381,
  [SMALL_STATE(67)] = 4451,
  [SMALL_STATE(68)] = 4513,
  [SMALL_STATE(69)] = 4575,
  [SMALL_STATE(70)] = 4637,
  [SMALL_STATE(71)] = 4699,
  [SMALL_STATE(72)] = 4736,
  [SMALL_STATE(73)] = 4773,
  [SMALL_STATE(74)] = 4810,
  [SMALL_STATE(75)] = 4849,
  [SMALL_STATE(76)] = 4908,
  [SMALL_STATE(77)] = 4944,
  [SMALL_STATE(78)] = 4980,
  [SMALL_STATE(79)] = 5016,
  [SMALL_STATE(80)] = 5056,
  [SMALL_STATE(81)] = 5092,
  [SMALL_STATE(82)] = 5128,
  [SMALL_STATE(83)] = 5164,
  [SMALL_STATE(84)] = 5223,
  [SMALL_STATE(85)] = 5257,
  [SMALL_STATE(86)] = 5311,
  [SMALL_STATE(87)] = 5357,
  [SMALL_STATE(88)] = 5391,
  [SMALL_STATE(89)] = 5449,
  [SMALL_STATE(90)] = 5497,
  [SMALL_STATE(91)] = 5547,
  [SMALL_STATE(92)] = 5587,
  [SMALL_STATE(93)] = 5639,
  [SMALL_STATE(94)] = 5693,
  [SMALL_STATE(95)] = 5727,
  [SMALL_STATE(96)] = 5765,
  [SMALL_STATE(97)] = 5821,
  [SMALL_STATE(98)] = 5877,
  [SMALL_STATE(99)] = 5925,
  [SMALL_STATE(100)] = 5969,
  [SMALL_STATE(101)] = 6003,
  [SMALL_STATE(102)] = 6036,
  [SMALL_STATE(103)] = 6075,
  [SMALL_STATE(104)] = 6127,
  [SMALL_STATE(105)] = 6179,
  [SMALL_STATE(106)] = 6231,
  [SMALL_STATE(107)] = 6283,
  [SMALL_STATE(108)] = 6335,
  [SMALL_STATE(109)] = 6387,
  [SMALL_STATE(110)] = 6442,
  [SMALL_STATE(111)] = 6491,
  [SMALL_STATE(112)] = 6537,
  [SMALL_STATE(113)] = 6567,
  [SMALL_STATE(114)] = 6597,
  [SMALL_STATE(115)] = 6651,
  [SMALL_STATE(116)] = 6707,
  [SMALL_STATE(117)] = 6737,
  [SMALL_STATE(118)] = 6783,
  [SMALL_STATE(119)] = 6829,
  [SMALL_STATE(120)] = 6875,
  [SMALL_STATE(121)] = 6905,
  [SMALL_STATE(122)] = 6938,
  [SMALL_STATE(123)] = 6991,
  [SMALL_STATE(124)] = 7044,
  [SMALL_STATE(125)] = 7097,
  [SMALL_STATE(126)] = 7132,
  [SMALL_STATE(127)] = 7169,
  [SMALL_STATE(128)] = 7222,
  [SMALL_STATE(129)] = 7259,
  [SMALL_STATE(130)] = 7312,
  [SMALL_STATE(131)] = 7365,
  [SMALL_STATE(132)] = 7398,
  [SMALL_STATE(133)] = 7433,
  [SMALL_STATE(134)] = 7468,
  [SMALL_STATE(135)] = 7496,
  [SMALL_STATE(136)] = 7542,
  [SMALL_STATE(137)] = 7568,
  [SMALL_STATE(138)] = 7594,
  [SMALL_STATE(139)] = 7622,
  [SMALL_STATE(140)] = 7662,
  [SMALL_STATE(141)] = 7688,
  [SMALL_STATE(142)] = 7714,
  [SMALL_STATE(143)] = 7740,
  [SMALL_STATE(144)] = 7766,
  [SMALL_STATE(145)] = 7791,
  [SMALL_STATE(146)] = 7820,
  [SMALL_STATE(147)] = 7851,
  [SMALL_STATE(148)] = 7876,
  [SMALL_STATE(149)] = 7901,
  [SMALL_STATE(150)] = 7932,
  [SMALL_STATE(151)] = 7969,
  [SMALL_STATE(152)] = 8006,
  [SMALL_STATE(153)] = 8031,
  [SMALL_STATE(154)] = 8056,
  [SMALL_STATE(155)] = 8081,
  [SMALL_STATE(156)] = 8105,
  [SMALL_STATE(157)] = 8129,
  [SMALL_STATE(158)] = 8155,
  [SMALL_STATE(159)] = 8179,
  [SMALL_STATE(160)] = 8203,
  [SMALL_STATE(161)] = 8241,
  [SMALL_STATE(162)] = 8265,
  [SMALL_STATE(163)] = 8289,
  [SMALL_STATE(164)] = 8329,
  [SMALL_STATE(165)] = 8364,
  [SMALL_STATE(166)] = 8399,
  [SMALL_STATE(167)] = 8422,
  [SMALL_STATE(168)] = 8445,
  [SMALL_STATE(169)] = 8470,
  [SMALL_STATE(170)] = 8505,
  [SMALL_STATE(171)] = 8528,
  [SMALL_STATE(172)] = 8551,
  [SMALL_STATE(173)] = 8586,
  [SMALL_STATE(174)] = 8609,
  [SMALL_STATE(175)] = 8632,
  [SMALL_STATE(176)] = 8655,
  [SMALL_STATE(177)] = 8678,
  [SMALL_STATE(178)] = 8713,
  [SMALL_STATE(179)] = 8736,
  [SMALL_STATE(180)] = 8768,
  [SMALL_STATE(181)] = 8798,
  [SMALL_STATE(182)] = 8828,
  [SMALL_STATE(183)] = 8860,
  [SMALL_STATE(184)] = 8892,
  [SMALL_STATE(185)] = 8913,
  [SMALL_STATE(186)] = 8934,
  [SMALL_STATE(187)] = 8964,
  [SMALL_STATE(188)] = 8994,
  [SMALL_STATE(189)] = 9022,
  [SMALL_STATE(190)] = 9048,
  [SMALL_STATE(191)] = 9077,
  [SMALL_STATE(192)] = 9104,
  [SMALL_STATE(193)] = 9124,
  [SMALL_STATE(194)] = 9150,
  [SMALL_STATE(195)] = 9171,
  [SMALL_STATE(196)] = 9191,
  [SMALL_STATE(197)] = 9211,
  [SMALL_STATE(198)] = 9226,
  [SMALL_STATE(199)] = 9243,
  [SMALL_STATE(200)] = 9260,
  [SMALL_STATE(201)] = 9277,
  [SMALL_STATE(202)] = 9294,
  [SMALL_STATE(203)] = 9311,
  [SMALL_STATE(204)] = 9328,
  [SMALL_STATE(205)] = 9345,
  [SMALL_STATE(206)] = 9362,
  [SMALL_STATE(207)] = 9374,
  [SMALL_STATE(208)] = 9390,
  [SMALL_STATE(209)] = 9404,
  [SMALL_STATE(210)] = 9418,
  [SMALL_STATE(211)] = 9432,
  [SMALL_STATE(212)] = 9446,
  [SMALL_STATE(213)] = 9460,
  [SMALL_STATE(214)] = 9476,
  [SMALL_STATE(215)] = 9488,
  [SMALL_STATE(216)] = 9502,
  [SMALL_STATE(217)] = 9516,
  [SMALL_STATE(218)] = 9530,
  [SMALL_STATE(219)] = 9541,
  [SMALL_STATE(220)] = 9556,
  [SMALL_STATE(221)] = 9571,
  [SMALL_STATE(222)] = 9586,
  [SMALL_STATE(223)] = 9597,
  [SMALL_STATE(224)] = 9612,
  [SMALL_STATE(225)] = 9627,
  [SMALL_STATE(226)] = 9642,
  [SMALL_STATE(227)] = 9653,
  [SMALL_STATE(228)] = 9664,
  [SMALL_STATE(229)] = 9681,
  [SMALL_STATE(230)] = 9696,
  [SMALL_STATE(231)] = 9713,
  [SMALL_STATE(232)] = 9728,
  [SMALL_STATE(233)] = 9742,
  [SMALL_STATE(234)] = 9756,
  [SMALL_STATE(235)] = 9770,
  [SMALL_STATE(236)] = 9784,
  [SMALL_STATE(237)] = 9798,
  [SMALL_STATE(238)] = 9812,
  [SMALL_STATE(239)] = 9826,
  [SMALL_STATE(240)] = 9840,
  [SMALL_STATE(241)] = 9854,
  [SMALL_STATE(242)] = 9868,
  [SMALL_STATE(243)] = 9882,
  [SMALL_STATE(244)] = 9896,
  [SMALL_STATE(245)] = 9910,
  [SMALL_STATE(246)] = 9924,
  [SMALL_STATE(247)] = 9934,
  [SMALL_STATE(248)] = 9948,
  [SMALL_STATE(249)] = 9962,
  [SMALL_STATE(250)] = 9976,
  [SMALL_STATE(251)] = 9990,
  [SMALL_STATE(252)] = 10000,
  [SMALL_STATE(253)] = 10014,
  [SMALL_STATE(254)] = 10028,
  [SMALL_STATE(255)] = 10042,
  [SMALL_STATE(256)] = 10056,
  [SMALL_STATE(257)] = 10066,
  [SMALL_STATE(258)] = 10080,
  [SMALL_STATE(259)] = 10090,
  [SMALL_STATE(260)] = 10104,
  [SMALL_STATE(261)] = 10118,
  [SMALL_STATE(262)] = 10132,
  [SMALL_STATE(263)] = 10142,
  [SMALL_STATE(264)] = 10156,
  [SMALL_STATE(265)] = 10166,
  [SMALL_STATE(266)] = 10180,
  [SMALL_STATE(267)] = 10194,
  [SMALL_STATE(268)] = 10208,
  [SMALL_STATE(269)] = 10222,
  [SMALL_STATE(270)] = 10236,
  [SMALL_STATE(271)] = 10250,
  [SMALL_STATE(272)] = 10264,
  [SMALL_STATE(273)] = 10273,
  [SMALL_STATE(274)] = 10282,
  [SMALL_STATE(275)] = 10291,
  [SMALL_STATE(276)] = 10302,
  [SMALL_STATE(277)] = 10311,
  [SMALL_STATE(278)] = 10322,
  [SMALL_STATE(279)] = 10333,
  [SMALL_STATE(280)] = 10342,
  [SMALL_STATE(281)] = 10353,
  [SMALL_STATE(282)] = 10362,
  [SMALL_STATE(283)] = 10371,
  [SMALL_STATE(284)] = 10382,
  [SMALL_STATE(285)] = 10391,
  [SMALL_STATE(286)] = 10400,
  [SMALL_STATE(287)] = 10409,
  [SMALL_STATE(288)] = 10418,
  [SMALL_STATE(289)] = 10429,
  [SMALL_STATE(290)] = 10440,
  [SMALL_STATE(291)] = 10451,
  [SMALL_STATE(292)] = 10460,
  [SMALL_STATE(293)] = 10469,
  [SMALL_STATE(294)] = 10478,
  [SMALL_STATE(295)] = 10487,
  [SMALL_STATE(296)] = 10496,
  [SMALL_STATE(297)] = 10505,
  [SMALL_STATE(298)] = 10514,
  [SMALL_STATE(299)] = 10523,
  [SMALL_STATE(300)] = 10531,
  [SMALL_STATE(301)] = 10539,
  [SMALL_STATE(302)] = 10547,
  [SMALL_STATE(303)] = 10555,
  [SMALL_STATE(304)] = 10563,
  [SMALL_STATE(305)] = 10571,
  [SMALL_STATE(306)] = 10579,
  [SMALL_STATE(307)] = 10587,
  [SMALL_STATE(308)] = 10595,
  [SMALL_STATE(309)] = 10603,
  [SMALL_STATE(310)] = 10611,
  [SMALL_STATE(311)] = 10619,
  [SMALL_STATE(312)] = 10627,
  [SMALL_STATE(313)] = 10635,
  [SMALL_STATE(314)] = 10643,
  [SMALL_STATE(315)] = 10651,
  [SMALL_STATE(316)] = 10659,
  [SMALL_STATE(317)] = 10667,
  [SMALL_STATE(318)] = 10675,
  [SMALL_STATE(319)] = 10683,
  [SMALL_STATE(320)] = 10691,
  [SMALL_STATE(321)] = 10699,
  [SMALL_STATE(322)] = 10707,
  [SMALL_STATE(323)] = 10715,
  [SMALL_STATE(324)] = 10723,
  [SMALL_STATE(325)] = 10731,
  [SMALL_STATE(326)] = 10739,
  [SMALL_STATE(327)] = 10747,
  [SMALL_STATE(328)] = 10755,
  [SMALL_STATE(329)] = 10763,
};

static const TSParseActionEntry ts_parse_actions[] = {
  [0] = {.entry = {.count = 0, .reusable = false}},
  [1] = {.entry = {.count = 1, .reusable = false}}, RECOVER(),
  [3] = {.entry = {.count = 1, .reusable = true}}, SHIFT_EXTRA(),
  [5] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_program, 0),
  [7] = {.entry = {.count = 1, .reusable = false}}, SHIFT(62),
  [9] = {.entry = {.count = 1, .reusable = false}}, SHIFT(82),
  [11] = {.entry = {.count = 1, .reusable = true}}, SHIFT(82),
  [13] = {.entry = {.count = 1, .reusable = true}}, SHIFT(231),
  [15] = {.entry = {.count = 1, .reusable = true}}, SHIFT(12),
  [17] = {.entry = {.count = 1, .reusable = true}}, SHIFT(38),
  [19] = {.entry = {.count = 1, .reusable = true}}, SHIFT(4),
  [21] = {.entry = {.count = 1, .reusable = false}}, SHIFT(32),
  [23] = {.entry = {.count = 1, .reusable = false}}, SHIFT(15),
  [25] = {.entry = {.count = 1, .reusable = false}}, SHIFT(31),
  [27] = {.entry = {.count = 1, .reusable = true}}, SHIFT(210),
  [29] = {.entry = {.count = 1, .reusable = false}}, SHIFT(212),
  [31] = {.entry = {.count = 1, .reusable = false}}, SHIFT(199),
  [33] = {.entry = {.count = 1, .reusable = false}}, SHIFT(121),
  [35] = {.entry = {.count = 1, .reusable = false}}, SHIFT(304),
  [37] = {.entry = {.count = 1, .reusable = false}}, SHIFT(325),
  [39] = {.entry = {.count = 1, .reusable = false}}, SHIFT(170),
  [41] = {.entry = {.count = 1, .reusable = false}}, SHIFT(166),
  [43] = {.entry = {.count = 1, .reusable = false}}, SHIFT(173),
  [45] = {.entry = {.count = 1, .reusable = false}}, SHIFT(74),
  [47] = {.entry = {.count = 1, .reusable = false}}, SHIFT(80),
  [49] = {.entry = {.count = 1, .reusable = false}}, SHIFT(324),
  [51] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_program_repeat1, 2),
  [53] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(62),
  [56] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(82),
  [59] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(82),
  [62] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(231),
  [65] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(12),
  [68] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(38),
  [71] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(4),
  [74] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(32),
  [77] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(15),
  [80] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(31),
  [83] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(210),
  [86] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(212),
  [89] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(199),
  [92] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(121),
  [95] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(304),
  [98] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(325),
  [101] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(170),
  [104] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(166),
  [107] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(173),
  [110] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(74),
  [113] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(80),
  [116] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_program_repeat1, 2), SHIFT_REPEAT(324),
  [119] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_program, 1),
  [121] = {.entry = {.count = 1, .reusable = true}}, SHIFT(43),
  [123] = {.entry = {.count = 1, .reusable = true}}, SHIFT(54),
  [125] = {.entry = {.count = 1, .reusable = true}}, SHIFT(327),
  [127] = {.entry = {.count = 1, .reusable = true}}, SHIFT(6),
  [129] = {.entry = {.count = 1, .reusable = true}}, SHIFT(246),
  [131] = {.entry = {.count = 1, .reusable = false}}, SHIFT(86),
  [133] = {.entry = {.count = 1, .reusable = true}}, SHIFT(116),
  [135] = {.entry = {.count = 1, .reusable = true}}, SHIFT(140),
  [137] = {.entry = {.count = 1, .reusable = true}}, SHIFT(251),
  [139] = {.entry = {.count = 1, .reusable = true}}, SHIFT(258),
  [141] = {.entry = {.count = 1, .reusable = false}}, SHIFT(98),
  [143] = {.entry = {.count = 1, .reusable = true}}, SHIFT(209),
  [145] = {.entry = {.count = 1, .reusable = true}}, SHIFT(47),
  [147] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_interface_declaration, 4, .production_id = 20),
  [149] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_interface_declaration, 4, .production_id = 20),
  [151] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_single_type_import, 3),
  [153] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_single_type_import, 3),
  [155] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_expression_statement, 2),
  [157] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_expression_statement, 2),
  [159] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_static_import_on_demand, 6, .production_id = 37),
  [161] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_static_import_on_demand, 6, .production_id = 37),
  [163] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_interface_body, 2),
  [165] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_interface_body, 2),
  [167] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_statement, 1),
  [169] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_statement, 1),
  [171] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_single_static_import, 6, .production_id = 36),
  [173] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_single_static_import, 6, .production_id = 36),
  [175] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_local_variable_declaration, 3, .production_id = 13),
  [177] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_local_variable_declaration, 3, .production_id = 13),
  [179] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_interface_declaration, 5, .production_id = 34),
  [181] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_interface_declaration, 5, .production_id = 34),
  [183] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_interface_declaration, 4, .production_id = 23),
  [185] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_interface_declaration, 4, .production_id = 23),
  [187] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_block, 2),
  [189] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_block, 2),
  [191] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_import_declaration, 1),
  [193] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_import_declaration, 1),
  [195] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_parcelable_body, 3),
  [197] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_parcelable_body, 3),
  [199] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_parcelable_declaration, 3, .production_id = 7),
  [201] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_parcelable_declaration, 3, .production_id = 7),
  [203] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_return_statement, 2),
  [205] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_return_statement, 2),
  [207] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_package_declaration, 3, .production_id = 2),
  [209] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_package_declaration, 3, .production_id = 2),
  [211] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_interface_declaration, 3, .production_id = 7),
  [213] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_interface_declaration, 3, .production_id = 7),
  [215] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_parcelable_declaration, 3, .production_id = 2),
  [217] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_parcelable_declaration, 3, .production_id = 2),
  [219] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_declaration, 1),
  [221] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_declaration, 1),
  [223] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_parcelable_body, 2),
  [225] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_parcelable_body, 2),
  [227] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_interface_body, 3),
  [229] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_interface_body, 3),
  [231] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_block, 3),
  [233] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_block, 3),
  [235] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_assert_statement, 3),
  [237] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_assert_statement, 3),
  [239] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_local_variable_declaration, 4, .production_id = 24),
  [241] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_local_variable_declaration, 4, .production_id = 24),
  [243] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_package_declaration, 4, .production_id = 28),
  [245] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_package_declaration, 4, .production_id = 28),
  [247] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_return_statement, 3),
  [249] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_return_statement, 3),
  [251] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_assert_statement, 5),
  [253] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_assert_statement, 5),
  [255] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_yield_statement, 3),
  [257] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_yield_statement, 3),
  [259] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_import_on_demand, 5),
  [261] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_type_import_on_demand, 5),
  [263] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym__unannotated_type, 1, .production_id = 1),
  [265] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_primary_expression, 1),
  [267] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_primary_expression, 1),
  [269] = {.entry = {.count = 1, .reusable = false}}, SHIFT(25),
  [271] = {.entry = {.count = 2, .reusable = false}}, REDUCE(sym_primary_expression, 1), SHIFT(83),
  [274] = {.entry = {.count = 2, .reusable = true}}, REDUCE(sym_primary_expression, 1), REDUCE(sym__unannotated_type, 1, .production_id = 1),
  [277] = {.entry = {.count = 3, .reusable = true}}, REDUCE(sym_primary_expression, 1), REDUCE(sym__unannotated_type, 1, .production_id = 1), SHIFT(198),
  [281] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__unannotated_type, 1, .production_id = 1),
  [283] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_interface_body_repeat1, 2), SHIFT_REPEAT(146),
  [286] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_interface_body_repeat1, 2), SHIFT_REPEAT(195),
  [289] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_interface_body_repeat1, 2),
  [291] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_interface_body_repeat1, 2), SHIFT_REPEAT(209),
  [294] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_interface_body_repeat1, 2), SHIFT_REPEAT(133),
  [297] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_interface_body_repeat1, 2), SHIFT_REPEAT(170),
  [300] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_interface_body_repeat1, 2), SHIFT_REPEAT(166),
  [303] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_interface_body_repeat1, 2), SHIFT_REPEAT(173),
  [306] = {.entry = {.count = 1, .reusable = false}}, SHIFT(146),
  [308] = {.entry = {.count = 1, .reusable = true}}, SHIFT(195),
  [310] = {.entry = {.count = 1, .reusable = true}}, SHIFT(37),
  [312] = {.entry = {.count = 1, .reusable = false}}, SHIFT(133),
  [314] = {.entry = {.count = 1, .reusable = true}}, SHIFT(53),
  [316] = {.entry = {.count = 1, .reusable = true}}, SHIFT(227),
  [318] = {.entry = {.count = 1, .reusable = true}}, SHIFT(52),
  [320] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_parcelable_body_repeat1, 2), SHIFT_REPEAT(146),
  [323] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_parcelable_body_repeat1, 2),
  [325] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_parcelable_body_repeat1, 2), SHIFT_REPEAT(209),
  [328] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_parcelable_body_repeat1, 2), SHIFT_REPEAT(133),
  [331] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_parcelable_body_repeat1, 2), SHIFT_REPEAT(170),
  [334] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_parcelable_body_repeat1, 2), SHIFT_REPEAT(166),
  [337] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_parcelable_body_repeat1, 2), SHIFT_REPEAT(173),
  [340] = {.entry = {.count = 1, .reusable = true}}, SHIFT(218),
  [342] = {.entry = {.count = 1, .reusable = true}}, SHIFT(45),
  [344] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_field_access, 3, .production_id = 9),
  [346] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_field_access, 3, .production_id = 9),
  [348] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_array_access, 4, .production_id = 22),
  [350] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_array_access, 4, .production_id = 22),
  [352] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_field_access, 5, .production_id = 33),
  [354] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_field_access, 5, .production_id = 33),
  [356] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_class_literal, 3, .dynamic_precedence = 17),
  [358] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_class_literal, 3, .dynamic_precedence = 17),
  [360] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_parenthesized_expression, 3),
  [362] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_parenthesized_expression, 3),
  [364] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_string_literal, 3),
  [366] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_string_literal, 3),
  [368] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_expression, 1),
  [370] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_expression, 1),
  [372] = {.entry = {.count = 1, .reusable = true}}, SHIFT(29),
  [374] = {.entry = {.count = 1, .reusable = true}}, SHIFT(197),
  [376] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_string_literal, 2),
  [378] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_string_literal, 2),
  [380] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym__literal, 1),
  [382] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__literal, 1),
  [384] = {.entry = {.count = 1, .reusable = true}}, SHIFT(178),
  [386] = {.entry = {.count = 1, .reusable = true}}, SHIFT(213),
  [388] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_cast_expression, 4, .production_id = 17),
  [390] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_cast_expression, 4, .production_id = 17),
  [392] = {.entry = {.count = 1, .reusable = false}}, SHIFT(21),
  [394] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_binary_expression, 3, .production_id = 11),
  [396] = {.entry = {.count = 1, .reusable = false}}, SHIFT(20),
  [398] = {.entry = {.count = 1, .reusable = true}}, SHIFT(20),
  [400] = {.entry = {.count = 1, .reusable = true}}, SHIFT(19),
  [402] = {.entry = {.count = 1, .reusable = true}}, SHIFT(16),
  [404] = {.entry = {.count = 1, .reusable = true}}, SHIFT(23),
  [406] = {.entry = {.count = 1, .reusable = false}}, SHIFT(23),
  [408] = {.entry = {.count = 1, .reusable = false}}, SHIFT(27),
  [410] = {.entry = {.count = 1, .reusable = true}}, SHIFT(28),
  [412] = {.entry = {.count = 1, .reusable = true}}, SHIFT(22),
  [414] = {.entry = {.count = 1, .reusable = false}}, SHIFT(22),
  [416] = {.entry = {.count = 1, .reusable = false}}, SHIFT(14),
  [418] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_cast_expression, 5, .production_id = 29),
  [420] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_cast_expression, 5, .production_id = 29),
  [422] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_assignment_expression, 3, .production_id = 8),
  [424] = {.entry = {.count = 1, .reusable = true}}, SHIFT(18),
  [426] = {.entry = {.count = 1, .reusable = true}}, SHIFT(17),
  [428] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_binary_expression, 3, .production_id = 11),
  [430] = {.entry = {.count = 2, .reusable = false}}, REDUCE(sym_primary_expression, 1), REDUCE(sym__unannotated_type, 1, .production_id = 1),
  [433] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_scoped_identifier, 3, .production_id = 19),
  [435] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_scoped_identifier, 3, .production_id = 19),
  [437] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_marker_annotation, 2, .production_id = 2),
  [439] = {.entry = {.count = 1, .reusable = true}}, SHIFT(7),
  [441] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_marker_annotation, 2, .production_id = 2),
  [443] = {.entry = {.count = 1, .reusable = true}}, SHIFT(312),
  [445] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__element_value, 1),
  [447] = {.entry = {.count = 1, .reusable = true}}, SHIFT(207),
  [449] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_annotation_argument_list, 4),
  [451] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_annotation_argument_list, 4),
  [453] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_annotation_argument_list, 3),
  [455] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_annotation_argument_list, 3),
  [457] = {.entry = {.count = 1, .reusable = true}}, SHIFT(55),
  [459] = {.entry = {.count = 1, .reusable = true}}, SHIFT(24),
  [461] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_annotation_argument_list, 2),
  [463] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_annotation_argument_list, 2),
  [465] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_annotation, 3, .production_id = 6),
  [467] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_annotation, 3, .production_id = 6),
  [469] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_modifiers, 1),
  [471] = {.entry = {.count = 1, .reusable = false}}, SHIFT(131),
  [473] = {.entry = {.count = 1, .reusable = true}}, SHIFT(72),
  [475] = {.entry = {.count = 1, .reusable = true}}, SHIFT(35),
  [477] = {.entry = {.count = 1, .reusable = true}}, SHIFT(59),
  [479] = {.entry = {.count = 1, .reusable = false}}, REDUCE(aux_sym_modifiers_repeat1, 2),
  [481] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_modifiers_repeat1, 2),
  [483] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_modifiers_repeat1, 2), SHIFT_REPEAT(209),
  [486] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_modifiers_repeat1, 2), SHIFT_REPEAT(125),
  [489] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_dimensions, 1),
  [491] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_dimensions, 1),
  [493] = {.entry = {.count = 1, .reusable = true}}, SHIFT(317),
  [495] = {.entry = {.count = 1, .reusable = true}}, SHIFT(60),
  [497] = {.entry = {.count = 1, .reusable = false}}, REDUCE(aux_sym_dimensions_repeat1, 2),
  [499] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_dimensions_repeat1, 2),
  [501] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_dimensions_repeat1, 2), SHIFT_REPEAT(317),
  [504] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_dimensions_repeat1, 2), SHIFT_REPEAT(210),
  [507] = {.entry = {.count = 1, .reusable = true}}, SHIFT(77),
  [509] = {.entry = {.count = 1, .reusable = true}}, SHIFT(58),
  [511] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_modifiers_repeat1, 2), SHIFT_REPEAT(210),
  [514] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_modifiers_repeat1, 2), SHIFT_REPEAT(131),
  [517] = {.entry = {.count = 1, .reusable = true}}, SHIFT(8),
  [519] = {.entry = {.count = 1, .reusable = true}}, SHIFT(326),
  [521] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_modifiers, 1),
  [523] = {.entry = {.count = 1, .reusable = false}}, SHIFT(125),
  [525] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_method_declaration, 2, .production_id = 32),
  [527] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_method_declaration, 2, .production_id = 32),
  [529] = {.entry = {.count = 1, .reusable = false}}, REDUCE(aux_sym_modifiers_repeat1, 1),
  [531] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_dimensions_expr_repeat1, 1), REDUCE(aux_sym_modifiers_repeat1, 1),
  [534] = {.entry = {.count = 1, .reusable = false}}, REDUCE(aux_sym_dimensions_expr_repeat1, 1),
  [536] = {.entry = {.count = 1, .reusable = false}}, SHIFT(318),
  [538] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_method_declaration, 3, .production_id = 38),
  [540] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_method_declaration, 3, .production_id = 38),
  [542] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_receiver_parameter, 5),
  [544] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_receiver_parameter, 5),
  [546] = {.entry = {.count = 1, .reusable = false}}, REDUCE(aux_sym_dimensions_expr_repeat1, 2),
  [548] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_dimensions_expr_repeat1, 2),
  [550] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_dimensions_expr_repeat1, 2), SHIFT_REPEAT(209),
  [553] = {.entry = {.count = 1, .reusable = true}}, SHIFT(83),
  [555] = {.entry = {.count = 1, .reusable = false}}, SHIFT(198),
  [557] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_receiver_parameter, 2),
  [559] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_receiver_parameter, 2),
  [561] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_field_declaration, 4, .production_id = 24),
  [563] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_field_declaration, 4, .production_id = 24),
  [565] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym__unannotated_type, 1),
  [567] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__unannotated_type, 1),
  [569] = {.entry = {.count = 1, .reusable = false}}, SHIFT(201),
  [571] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_receiver_parameter, 3),
  [573] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_receiver_parameter, 3),
  [575] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_field_declaration, 3, .production_id = 13),
  [577] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_field_declaration, 3, .production_id = 13),
  [579] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_receiver_parameter, 4),
  [581] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_receiver_parameter, 4),
  [583] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_scoped_type_identifier, 3, .production_id = 15),
  [585] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_scoped_type_identifier, 3, .production_id = 15),
  [587] = {.entry = {.count = 2, .reusable = false}}, REDUCE(aux_sym_dimensions_expr_repeat1, 1), REDUCE(aux_sym_modifiers_repeat1, 1),
  [590] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_scoped_type_identifier, 4, .production_id = 27),
  [592] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_scoped_type_identifier, 4, .production_id = 27),
  [594] = {.entry = {.count = 1, .reusable = false}}, REDUCE(aux_sym_dimensions_repeat1, 3),
  [596] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_dimensions_repeat1, 3),
  [598] = {.entry = {.count = 1, .reusable = false}}, SHIFT(188),
  [600] = {.entry = {.count = 1, .reusable = true}}, SHIFT(316),
  [602] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_scoped_type_identifier, 3, .production_id = 10),
  [604] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_scoped_type_identifier, 3, .production_id = 10),
  [606] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_scoped_type_identifier, 4, .production_id = 21),
  [608] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_scoped_type_identifier, 4, .production_id = 21),
  [610] = {.entry = {.count = 1, .reusable = false}}, SHIFT(187),
  [612] = {.entry = {.count = 1, .reusable = true}}, SHIFT(215),
  [614] = {.entry = {.count = 1, .reusable = false}}, SHIFT(147),
  [616] = {.entry = {.count = 1, .reusable = true}}, SHIFT(208),
  [618] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_floating_point_type, 1),
  [620] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_floating_point_type, 1),
  [622] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_array_type, 2, .production_id = 4),
  [624] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_array_type, 2, .production_id = 4),
  [626] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_integral_type, 1),
  [628] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_integral_type, 1),
  [630] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_type_arguments, 3),
  [632] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_arguments, 3),
  [634] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_generic_type, 2, .dynamic_precedence = 10, .production_id = 1),
  [636] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_generic_type, 2, .dynamic_precedence = 10, .production_id = 1),
  [638] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_generic_type, 2, .dynamic_precedence = 10),
  [640] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_generic_type, 2, .dynamic_precedence = 10),
  [642] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_type_arguments, 4),
  [644] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_arguments, 4),
  [646] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_type_arguments, 2),
  [648] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_arguments, 2),
  [650] = {.entry = {.count = 1, .reusable = false}}, SHIFT(277),
  [652] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__type, 1),
  [654] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_annotated_type, 2),
  [656] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_type_parameters, 3),
  [658] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_parameters, 3),
  [660] = {.entry = {.count = 1, .reusable = false}}, REDUCE(sym_type_parameters, 4),
  [662] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_parameters, 4),
  [664] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_variable_declarator, 1, .production_id = 3),
  [666] = {.entry = {.count = 1, .reusable = true}}, SHIFT(302),
  [668] = {.entry = {.count = 2, .reusable = true}}, REDUCE(sym__unannotated_type, 1), SHIFT(201),
  [671] = {.entry = {.count = 1, .reusable = false}}, SHIFT(303),
  [673] = {.entry = {.count = 1, .reusable = false}}, SHIFT(152),
  [675] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_declarator, 2, .production_id = 40),
  [677] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_dimensions_expr_repeat1, 2), SHIFT_REPEAT(210),
  [680] = {.entry = {.count = 1, .reusable = true}}, SHIFT(220),
  [682] = {.entry = {.count = 1, .reusable = false}}, SHIFT(71),
  [684] = {.entry = {.count = 1, .reusable = false}}, SHIFT(307),
  [686] = {.entry = {.count = 1, .reusable = true}}, SHIFT(161),
  [688] = {.entry = {.count = 1, .reusable = false}}, SHIFT(283),
  [690] = {.entry = {.count = 1, .reusable = false}}, SHIFT(211),
  [692] = {.entry = {.count = 1, .reusable = true}}, SHIFT(301),
  [694] = {.entry = {.count = 1, .reusable = true}}, SHIFT(155),
  [696] = {.entry = {.count = 1, .reusable = true}}, SHIFT(217),
  [698] = {.entry = {.count = 1, .reusable = true}}, SHIFT(224),
  [700] = {.entry = {.count = 1, .reusable = true}}, SHIFT(162),
  [702] = {.entry = {.count = 1, .reusable = true}}, SHIFT(158),
  [704] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_wildcard, 2),
  [706] = {.entry = {.count = 1, .reusable = true}}, SHIFT(105),
  [708] = {.entry = {.count = 1, .reusable = false}}, SHIFT(132),
  [710] = {.entry = {.count = 1, .reusable = false}}, SHIFT(102),
  [712] = {.entry = {.count = 1, .reusable = false}}, SHIFT(311),
  [714] = {.entry = {.count = 1, .reusable = false}}, SHIFT(290),
  [716] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_wildcard, 1),
  [718] = {.entry = {.count = 1, .reusable = false}}, SHIFT(73),
  [720] = {.entry = {.count = 1, .reusable = false}}, SHIFT(288),
  [722] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_formal_parameters, 3),
  [724] = {.entry = {.count = 1, .reusable = false}}, REDUCE(aux_sym_string_literal_repeat1, 2),
  [726] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_string_literal_repeat1, 2), SHIFT_REPEAT(219),
  [729] = {.entry = {.count = 1, .reusable = false}}, SHIFT_EXTRA(),
  [731] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_parameter, 1, .production_id = 1),
  [733] = {.entry = {.count = 1, .reusable = true}}, SHIFT(106),
  [735] = {.entry = {.count = 1, .reusable = true}}, SHIFT(108),
  [737] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_bound, 2),
  [739] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_formal_parameters, 5),
  [741] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_type_bound_repeat1, 2), SHIFT_REPEAT(108),
  [744] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_type_bound_repeat1, 2),
  [746] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_parameter, 2, .production_id = 30),
  [748] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_bound, 3),
  [750] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_formal_parameters, 4),
  [752] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_formal_parameters, 2),
  [754] = {.entry = {.count = 1, .reusable = true}}, SHIFT(64),
  [756] = {.entry = {.count = 1, .reusable = false}}, SHIFT(78),
  [758] = {.entry = {.count = 1, .reusable = true}}, SHIFT(219),
  [760] = {.entry = {.count = 1, .reusable = false}}, SHIFT(81),
  [762] = {.entry = {.count = 1, .reusable = true}}, SHIFT(229),
  [764] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_type_parameters_repeat1, 2),
  [766] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_type_parameters_repeat1, 2), SHIFT_REPEAT(196),
  [769] = {.entry = {.count = 1, .reusable = true}}, SHIFT(185),
  [771] = {.entry = {.count = 1, .reusable = true}}, SHIFT(196),
  [773] = {.entry = {.count = 1, .reusable = true}}, SHIFT(137),
  [775] = {.entry = {.count = 1, .reusable = true}}, SHIFT(289),
  [777] = {.entry = {.count = 1, .reusable = true}}, SHIFT(9),
  [779] = {.entry = {.count = 1, .reusable = true}}, SHIFT(171),
  [781] = {.entry = {.count = 1, .reusable = true}}, SHIFT(97),
  [783] = {.entry = {.count = 1, .reusable = true}}, SHIFT(222),
  [785] = {.entry = {.count = 1, .reusable = true}}, SHIFT(75),
  [787] = {.entry = {.count = 1, .reusable = true}}, SHIFT(142),
  [789] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_formal_parameters_repeat1, 2),
  [791] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_formal_parameters_repeat1, 2), SHIFT_REPEAT(75),
  [794] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_header, 2, .production_id = 31),
  [796] = {.entry = {.count = 1, .reusable = true}}, SHIFT(103),
  [798] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_annotation_argument_list_repeat1, 2),
  [800] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_annotation_argument_list_repeat1, 2), SHIFT_REPEAT(289),
  [803] = {.entry = {.count = 1, .reusable = true}}, SHIFT(10),
  [805] = {.entry = {.count = 1, .reusable = true}}, SHIFT(256),
  [807] = {.entry = {.count = 1, .reusable = true}}, SHIFT(107),
  [809] = {.entry = {.count = 1, .reusable = true}}, SHIFT(26),
  [811] = {.entry = {.count = 1, .reusable = true}}, SHIFT(104),
  [813] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_throws, 3),
  [815] = {.entry = {.count = 1, .reusable = true}}, SHIFT(216),
  [817] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__variable_declarator_list, 2, .production_id = 14),
  [819] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_element_value_array_initializer, 2),
  [821] = {.entry = {.count = 1, .reusable = true}}, SHIFT(226),
  [823] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_type_list_repeat1, 2), SHIFT_REPEAT(104),
  [826] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_type_list_repeat1, 2),
  [828] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_header, 3, .production_id = 39),
  [830] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_element_value_array_initializer, 5),
  [832] = {.entry = {.count = 1, .reusable = true}}, SHIFT(112),
  [834] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_cast_expression_repeat1, 2, .production_id = 18), SHIFT_REPEAT(107),
  [837] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_cast_expression_repeat1, 2, .production_id = 18),
  [839] = {.entry = {.count = 1, .reusable = true}}, SHIFT(113),
  [841] = {.entry = {.count = 1, .reusable = true}}, SHIFT(278),
  [843] = {.entry = {.count = 1, .reusable = true}}, SHIFT(308),
  [845] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_element_value_array_initializer, 3),
  [847] = {.entry = {.count = 1, .reusable = true}}, SHIFT(50),
  [849] = {.entry = {.count = 1, .reusable = true}}, SHIFT(67),
  [851] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_element_value_array_initializer, 4),
  [853] = {.entry = {.count = 1, .reusable = true}}, SHIFT(101),
  [855] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_type_arguments_repeat1, 2),
  [857] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_type_arguments_repeat1, 2), SHIFT_REPEAT(97),
  [860] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym_element_value_array_initializer_repeat1, 2), SHIFT_REPEAT(13),
  [863] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_element_value_array_initializer_repeat1, 2),
  [865] = {.entry = {.count = 1, .reusable = true}}, SHIFT(184),
  [867] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_variable_declarator, 2, .production_id = 12),
  [869] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__variable_declarator_list, 1, .production_id = 5),
  [871] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_header, 4, .production_id = 41),
  [873] = {.entry = {.count = 2, .reusable = true}}, REDUCE(aux_sym__variable_declarator_list_repeat1, 2, .production_id = 26), SHIFT_REPEAT(216),
  [876] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym__variable_declarator_list_repeat1, 2, .production_id = 26),
  [878] = {.entry = {.count = 1, .reusable = true}}, SHIFT(176),
  [880] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_throws, 2),
  [882] = {.entry = {.count = 1, .reusable = true}}, SHIFT(30),
  [884] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_parameter, 3, .production_id = 30),
  [886] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_declarator, 3, .production_id = 42),
  [888] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym__variable_declarator_list_repeat1, 2, .production_id = 25),
  [890] = {.entry = {.count = 1, .reusable = true}}, SHIFT(66),
  [892] = {.entry = {.count = 1, .reusable = true}}, SHIFT(39),
  [894] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_wildcard, 3),
  [896] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_spread_parameter, 4),
  [898] = {.entry = {.count = 1, .reusable = true}}, SHIFT(259),
  [900] = {.entry = {.count = 1, .reusable = true}}, SHIFT(34),
  [902] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_formal_parameter, 2, .production_id = 43),
  [904] = {.entry = {.count = 1, .reusable = true}}, REDUCE(aux_sym_cast_expression_repeat1, 2, .production_id = 16),
  [906] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__wildcard_bounds, 2),
  [908] = {.entry = {.count = 1, .reusable = true}}, SHIFT(57),
  [910] = {.entry = {.count = 1, .reusable = true}}, SHIFT(329),
  [912] = {.entry = {.count = 1, .reusable = true}}, SHIFT(48),
  [914] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_formal_parameter, 3, .production_id = 16),
  [916] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_spread_parameter, 3),
  [918] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_type_parameter, 2, .production_id = 1),
  [920] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_element_value_pair, 3, .production_id = 35),
  [922] = {.entry = {.count = 1, .reusable = true}}, SHIFT(148),
  [924] = {.entry = {.count = 1, .reusable = true}}, SHIFT(159),
  [926] = {.entry = {.count = 1, .reusable = true}}, SHIFT(154),
  [928] = {.entry = {.count = 1, .reusable = true}}, SHIFT(309),
  [930] = {.entry = {.count = 1, .reusable = true}}, SHIFT(228),
  [932] = {.entry = {.count = 1, .reusable = true}}, SHIFT(56),
  [934] = {.entry = {.count = 1, .reusable = true}}, SHIFT(36),
  [936] = {.entry = {.count = 1, .reusable = true}}, SHIFT(214),
  [938] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_asterisk, 1),
  [940] = {.entry = {.count = 1, .reusable = true}}, SHIFT(144),
  [942] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_header, 5, .production_id = 41),
  [944] = {.entry = {.count = 1, .reusable = true}}, SHIFT(255),
  [946] = {.entry = {.count = 1, .reusable = true}}, SHIFT(61),
  [948] = {.entry = {.count = 1, .reusable = true}}, SHIFT(136),
  [950] = {.entry = {.count = 1, .reusable = true}}, SHIFT(40),
  [952] = {.entry = {.count = 1, .reusable = true}}, SHIFT(76),
  [954] = {.entry = {.count = 1, .reusable = true}}, SHIFT(156),
  [956] = {.entry = {.count = 1, .reusable = true}}, SHIFT(230),
  [958] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_header, 4, .production_id = 39),
  [960] = {.entry = {.count = 1, .reusable = true}}, SHIFT(143),
  [962] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym__method_header, 3, .production_id = 31),
  [964] = {.entry = {.count = 1, .reusable = true}}, SHIFT(153),
  [966] = {.entry = {.count = 1, .reusable = true}},  ACCEPT_INPUT(),
  [968] = {.entry = {.count = 1, .reusable = true}}, SHIFT(257),
  [970] = {.entry = {.count = 1, .reusable = true}}, SHIFT(134),
  [972] = {.entry = {.count = 1, .reusable = true}}, SHIFT(11),
};

#ifdef __cplusplus
extern "C" {
#endif
#ifdef _WIN32
#define extern __declspec(dllexport)
#endif

extern const TSLanguage *tree_sitter_aidl(void) {
  static const TSLanguage language = {
    .version = LANGUAGE_VERSION,
    .symbol_count = SYMBOL_COUNT,
    .alias_count = ALIAS_COUNT,
    .token_count = TOKEN_COUNT,
    .external_token_count = EXTERNAL_TOKEN_COUNT,
    .state_count = STATE_COUNT,
    .large_state_count = LARGE_STATE_COUNT,
    .production_id_count = PRODUCTION_ID_COUNT,
    .field_count = FIELD_COUNT,
    .max_alias_sequence_length = MAX_ALIAS_SEQUENCE_LENGTH,
    .parse_table = &ts_parse_table[0][0],
    .small_parse_table = ts_small_parse_table,
    .small_parse_table_map = ts_small_parse_table_map,
    .parse_actions = ts_parse_actions,
    .symbol_names = ts_symbol_names,
    .field_names = ts_field_names,
    .field_map_slices = ts_field_map_slices,
    .field_map_entries = ts_field_map_entries,
    .symbol_metadata = ts_symbol_metadata,
    .public_symbol_map = ts_symbol_map,
    .alias_map = ts_non_terminal_alias_map,
    .alias_sequences = &ts_alias_sequences[0][0],
    .lex_modes = ts_lex_modes,
    .lex_fn = ts_lex,
    .keyword_lex_fn = ts_lex_keywords,
    .keyword_capture_token = sym_identifier,
    .primary_state_ids = ts_primary_state_ids,
  };
  return &language;
}
#ifdef __cplusplus
}
#endif
