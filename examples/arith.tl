// Integer arithmetic, see docs/LANGUAGE.md section 4.2.
fn main() {
    var a = read();
    var b = read();
    print(a + b);
    print(a - b);
    print(a * b);
    print(a < b);
    print(a == b || a > 100);
    print(!a);
    print(2147483647 + 1);
}
