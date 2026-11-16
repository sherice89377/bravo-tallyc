# Open bug reports

Reports from users of `tallyc` 0.9. Nobody has triaged them yet: the "notes" are the reporters'
own guesses. Each program is in `examples/bugs/` with the input it was run with (`.in`). "Expected"
is what `bin/tallyc run` prints for that input.

## 1. The last value seen in a loop is 1 step too far (`examples/bugs/lastseen.tl`)

Input `50 7`. Wrong at the default `-O2`, right at `-O0`.

- Expected: `49`, `56`
- Got: `56`, `56`

Reporter's note: "Looks like loop inversion runs the loop body 1 extra time: `last` ends up equal to
`x`."

## 2. Rotating 3 values (`examples/bugs/rotate.tl`)

Input `1 2 3 4`. Wrong at `-O2`, right at `-O0`.

- Expected: `2`, `3`, `1`
- Got: `1`, `1`, `1`

Reporter's note: "All 3 variables end up with the same value. Copy propagation must be replacing
`t` with `a` after `a` has changed."

## 3. A generated kernel (`examples/bugs/kernel.tl`)

Input in `kernel.in`. Wrong at `-O2`, right at `-O0`; a smaller kernel of the same shape is right
at both levels.

- Expected: `280174`, `4288`, `4708`
- Got: `261804`, `0`, `31`

Reporter's note: "Our kernels come out of a code generator, so they are long. Something in constant
propagation goes wrong when there are many `%` operations."

## 4. A swap followed by a call (`examples/bugs/swap.tl`)

Input `4 9`. Wrong at `-O2`, right at `-O0`.

- Expected: `9`, `4`, `36`, `13`
- Got: `4`, `4`, `36`, `8`

Reporter's note: "Only at `-O2`, and it is a swap through `tmp`, so I would look at copy
propagation first."
