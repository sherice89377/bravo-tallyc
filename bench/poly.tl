// Evaluates a fixed polynomial at many points (Horner's rule), with the coefficients as constants.
fn main() {
    var n = read();
    var x = 0 - n;
    var acc = 0;
    while (x <= n) {
        var c4 = 3;
        var c3 = -2;
        var c2 = 7;
        var c1 = -5;
        var c0 = 11;
        var scale = c4 * 2 - c2 + 3;
        var y = c4;
        y = y * x + c3;
        y = y * x + c2;
        y = y * x + c1;
        y = y * x + c0;
        acc = acc + y / scale + y % 7;
        x = x + 1;
    }
    print(acc);
}
