// Prefix sums and a running checksum over an array read from the input.
fn main() {
    var n = read();
    var a = new int[n];
    var i = 0;
    var seed = read();
    while (i < n) {
        seed = (seed * 1103 + 12345) % 65536;
        a[i] = seed % 1000 - 500;
        i = i + 1;
    }
    var rounds = 0;
    var check = 0;
    while (rounds < 20) {
        var k = 1;
        var step = 1;
        var mask = 1023;
        while (k < n) {
            var modulus = (mask + 1) * 100 - 2400;
            var prime = modulus * 10 + 3;
            var mult = (step + 30) * step;
            a[k] = (a[k] + a[k - step]) % modulus;
            check = (check * mult + a[k]) % prime;
            k = k + step;
        }
        rounds = rounds + 1;
    }
    print(check);
    print(a[n - 1]);
}
