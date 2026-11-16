# TVM, the Tally virtual machine

`bin/tvm run FILE` runs a TVM assembly file, the output of `tallyc build`. The VM lives in
`src/tally/vm` and depends on nothing else in the repository.

## Machine

- 8 registers `r0` .. `r7` holding 32-bit ints, all 0 at start.
- A stack of 1048576 words growing down, a stack pointer `sp` and a frame pointer `fp`.
- A heap of int arrays. An array value in a register is a handle; `newarr` makes 1. Using a value
  that is not a handle as an array is the error `invalid array reference`.
- Execution starts by calling the label `main`; the run ends when `main` returns.

## Assembly format

1 instruction or label per line. `;` starts a comment. A label is a name followed by `:`.
Operands are separated by commas. A memory operand is `[fp+k]` or `[fp-k]`.

## Instructions

| Instruction | Effect | Cycles |
|---|---|---|
| `li rd, k` | rd = k | 1 |
| `mov rd, rs` | rd = rs | 1 |
| `add sub mul div rem rd, ra, rb` | rd = ra op rb (wrapping; div and rem truncate, error on 0) | 1, 1, 3, 8, 8 |
| `slt sle sgt sge seq sne rd, ra, rb` | rd = 1 if the comparison holds, else 0 | 1 |
| `neg rd, ra` / `not rd, ra` | rd = -ra / rd = (ra == 0) | 1 |
| `ld rd, [fp+k]` / `st rs, [fp+k]` | load / store a stack word | 2 |
| `push rs` / `pop rd` | sp = sp - 1, store / load, sp = sp + 1 | 2 |
| `newarr rd, rn` | rd = handle of a new array of rn zeros | 10 |
| `aload rd, ra, ri` | rd = ra[ri] | 3 |
| `astore ra, ri, rv` | ra[ri] = rv | 3 |
| `alen rd, ra` | rd = length of ra | 1 |
| `read rd` | rd = next input integer (0 when the input is used up) | 20 |
| `print rs` | write rs and a line break | 20 |
| `jmp L` / `bz rs, L` / `bnz rs, L` | jump / jump if rs == 0 / jump if rs != 0 | 1 |
| `call L` | push the return address, jump to L | 4 |
| `ret` | pop the return address and jump to it | 4 |
| `enter n` | push fp; fp = sp; sp = sp - n | 2 |
| `leave` | sp = fp; pop fp | 2 |
| `addsp n` | sp = sp + n | 1 |
| `halt` | stop | 1 |

After `call` and `enter`, `[fp+1]` is the return address and `[fp+2]`, `[fp+3]`, ... are the words
the caller pushed last, 2nd last, and so on.

## Errors

Runtime errors print `runtime error: <message>` to standard output and exit with status 1, exactly
like the Tally interpreter. Besides the Tally errors, a broken program can cause
`invalid array reference`, `invalid stack access`, `invalid jump`, `stack overflow` and
`cycle limit exceeded` (`--max-cycles N`, default 2000000000). `--stats` prints the cycle count to
standard error.
