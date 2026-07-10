/*
 *  This file is part of android-tree-sitter.
 *
 *  android-tree-sitter library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  android-tree-sitter library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.treesitter;

/**
 * A logger callback for {@link TSParser}.
 *
 * <p>This is the Java binding of tree-sitter 0.27 {@code TSLogger}. The parser will invoke
 * {@link #log(TSLogType, String)} during parsing to report parse/lex debug information.
 *
 * <p>This is a functional interface, so it can be implemented as a lambda:
 * <pre>{@code
 * parser.setLogger((type, buffer) -> {
 *   Log.d("TSParser", type + ": " + buffer);
 * });
 * }</pre>
 *
 * <p><strong>Lifecycle note:</strong> The native layer holds a global reference to the
 * {@link TSLogger} instance assigned via {@link TSParser#setLogger(TSLogger)}. That reference
 * is released when the parser is closed or when a new logger (or {@code null}) is assigned.
 * Callers are responsible for releasing any resources held by a <em>previously</em> assigned
 * logger.
 *
 * <p><strong>Threading note:</strong> The callback may be invoked on the thread that is
 * performing the parse (which is the caller's thread for the synchronous
 * {@code parseString} APIs). Implementations should avoid blocking operations and must not
 * call back into the same {@link TSParser} instance (re-entrancy is not supported).
 */
@FunctionalInterface
public interface TSLogger {

  /**
   * Called by the parser to emit a log message.
   *
   * @param logType the type of the log message ({@link TSLogType#PARSE} or {@link TSLogType#LEX}).
   * @param buffer  the log message content. May be empty but is never {@code null}.
   */
  void log(TSLogType logType, String buffer);
}
