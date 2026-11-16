# tallyc

An optimising compiler for Tally, a small imperative language used to teach compiler back ends,
and TVM, the register virtual machine it targets. Written in Java 21 with the JDK alone: no build
tool and no dependencies.

- `docs/LANGUAGE.md`: the language. `bin/tallyc run` (the interpreter in `src/tally/interp`) is its
  reference implementation.
- `docs/TVM.md`: the virtual machine and its assembly format.
- `BUGS.md`: open bug reports.

## Build and run

```sh
./build.sh                                  # compile into build/classes
./build.sh test                             # also run the test suite in tests/
bin/tallyc run examples/primes.tl           # interpret
bin/tallyc build examples/primes.tl -o primes.tvm    # compile (-O2 by default; -O0 also works)
bin/tvm run primes.tvm                      # run on the VM (--stats prints the cycle count)
bin/tallyc build examples/primes.tl --dump-ir -o primes.tvm   # print IR and allocation to stderr
```

Programs read their input with `read()` from standard input.

## How the compiler works

| Stage | Code |
|---|---|
| Lexer, parser, checker | `src/tally/front` |
| Lowering to IR (3-address code on virtual registers, basic blocks) | `src/tally/ir` |
| `-O2`: loop inversion, SSA construction, sparse conditional constant propagation, copy propagation, dead-code elimination, leaving SSA, copy coalescing | `src/tally/opt` |
| Live-variable analysis (`LiveSet`, `Liveness`) | `src/tally/opt` |
| Live intervals and linear-scan register allocation on r1 .. r6 | `src/tally/backend/Intervals.java`, `LinearScan.java` |
| TVM code generation, spill code, calling convention | `src/tally/backend/CodeGen.java` |
| The VM | `src/tally/vm` |

At `-O0` only the CFG cleanup runs; liveness, register allocation and code generation are the same
at both levels.

## Benchmarks

`bench/` holds small loop-heavy programs with their inputs (`NAME.in1`, `NAME.in2`). `-O2` should
run each of them in clearly fewer VM cycles than `-O0`; compare with `bin/tvm run FILE --stats`.

## Licence

MIT, see `LICENSE`.
