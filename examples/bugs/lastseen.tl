// Reported by the data team: the "last value below the limit" is wrong when compiled.
fn main() {
    var limit = read();
    var step = read();
    var x = 0;
    var last = -1;
    while (x < limit) {
        last = x;
        x = x + step;
    }
    print(last);
    print(x);
}
