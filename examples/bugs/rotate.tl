// Reported by a student: rotating 3 values in a loop gives repeated values.
fn main() {
    var a = read();
    var b = read();
    var c = read();
    var n = read();
    var k = 0;
    while (k < n) {
        var t = a;
        a = b;
        b = c;
        c = t;
        k = k + 1;
    }
    print(a);
    print(b);
    print(c);
}
