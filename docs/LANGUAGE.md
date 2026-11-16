# The Tally language

Tally is a small imperative language for teaching compiler back ends. A program is a list of
functions; it runs by calling `main`. This document is the specification: `tallyc run` (the
interpreter in `src/tally/interp`) implements it directly, and every compiled program must behave
the same way.

## 1. Lexical structure

- Whitespace separates tokens. `//` starts a comment that runs to the end of the line.
- Identifiers: a letter or `_`, then letters, digits or `_`. Keywords: `fn var if else while return
  print read new int len`.
- Integer literals: decimal digits, value at most 2147483647. There are no negative literals: `-5`
  is unary minus applied to `5`, and the smallest int is written `-2147483647 - 1`.
- Operators and punctuation: `( ) { } [ ] , ; : = + - * / % == != < <= > >= && || !`.

## 2. Grammar

```
program   = function*
function  = "fn" IDENT "(" [param ("," param)*] ")" [":" "int"] block
param     = IDENT ":" type
type      = "int" | "int" "[" "]"
block     = "{" statement* "}"
statement = "var" IDENT [":" type] "=" expr ";"
          | IDENT "=" expr ";"
          | postfix "[" expr "]" "=" expr ";"
          | "if" "(" expr ")" block ["else" (block | if-statement)]
          | "while" "(" expr ")" block
          | "return" [expr] ";"
          | "print" "(" expr ")" ";"
          | call ";"
          | block
expr      = or
or        = and ("||" and)*
and       = equality ("&&" equality)*
equality  = relational (("==" | "!=") relational)*
relational= additive (("<" | "<=" | ">" | ">=") additive)*
additive  = term (("+" | "-") term)*
term      = unary (("*" | "/" | "%") unary)*
unary     = ("-" | "!") unary | postfix
postfix   = primary ("[" expr "]")*
primary   = INT | IDENT | call | "(" expr ")" | "new" "int" "[" expr "]"
          | "len" "(" expr ")" | "read" "(" ")"
call      = IDENT "(" [expr ("," expr)*] ")"
```

All binary operators are left associative.

## 3. Static rules

- There are 2 value types: `int` (32-bit signed) and `int[]` (a reference to an array of ints).
- Every function name is unique. `main` must exist, take no parameters and return nothing; it
  cannot be called.
- A function returns `int` (declared with `: int`) or nothing. `return e;` is required to have an
  `int` value in an `int` function and is not allowed to have 1 in the other kind.
- A variable is declared with `var` and an initial value; its type is the value's type (or the
  declared type, which must match). A name is visible from its declaration to the end of its block.
  A declaration may not reuse a name that is visible at that point (no shadowing); separate blocks
  may reuse names.
- Arithmetic, comparison and logical operators, `if` and `while` conditions, indexes, array sizes
  and `print` take `int` operands. `len` and indexing take `int[]`.
- Calls must pass the declared number of arguments with the declared types. A call to a function
  that returns nothing can only be a statement.

## 4. Semantics

### 4.1 Evaluation

Expressions are evaluated left to right: both operands of a binary operator, the arguments of a
call, and, for `a[i] = v`, first `a`, then `i`, then `v`. `&&` and `||` evaluate their right
operand only when needed. Arrays are passed and stored by reference: a callee that writes an
element of an array argument changes the caller's array. Parameters and locals are private to each
call. Falling off the end of an `int` function returns 0.

### 4.2 Integers

`int` arithmetic is 32-bit signed and wraps around: `2147483647 + 1` is `-2147483648`,
and `-(-2147483647 - 1)` is `-2147483648`. Division truncates toward zero and the remainder has the
sign of the dividend: `-7 / 2` is `-3` and `-7 % 2` is `-1`; `7 / -2` is `-3` and `7 % -2` is `1`.
`(-2147483647 - 1) / -1` is `-2147483648` and the matching remainder is 0. Comparisons, `!`, `&&`
and `||` give 1 for true and 0 for false; any non-zero int counts as true.

### 4.3 Arrays

`new int[n]` makes an array of n zeros. `len(a)` is its length. Indexes run from 0 to `len(a) - 1`.

### 4.4 Input and output

`print(e)` writes the value of e in decimal followed by a line break. `read()` returns the next
integer of the program's standard input: the input is a sequence of integers in decimal (an
optional `-`, then digits) separated by spaces, tabs and line breaks. After every integer has been
read, `read()` returns 0.

### 4.5 Runtime errors

These stop the program. It writes `runtime error: ` and the message on its own line to standard
output (after everything printed so far) and exits with status 1:

| Message | When |
|---|---|
| `division by zero` | the right operand of `/` or `%` is 0 |
| `index out of bounds` | an index is below 0 or at least the array's length |
| `negative array size` | `new int[n]` with n below 0 |
| `array too large` | `new int[n]` with n above 16777216 |
| `invalid input` | the next input word is not an integer in int range |

A program that finishes `main` exits with status 0. Programs nested more than 1000 calls deep have
no defined behaviour.
