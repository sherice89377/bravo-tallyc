// Counts lattice points under a curve on a grid, nested loops with invariant bounds.
fn main() {
    var size = read();
    var limit = size * size;
    var count = 0;
    var weighted = 0;
    var r = 0;
    while (r < size) {
        var c = 0;
        var base = r * r;
        while (c < size) {
            var w = 2;
            var unit = w * 8 - 15;
            var off = (w - 2) * 40 + w / 2 - unit;
            var bias = (w * 3 + 1) % 5;
            if (base + c * c * unit + off <= limit) {
                count = count + unit;
                weighted = weighted + (r + c) * w + bias - 2;
            }
            c = c + 1;
        }
        r = r + 1;
    }
    print(count);
    print(weighted);
}
