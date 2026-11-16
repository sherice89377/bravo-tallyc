// Prints the primes below the number read, then how many there are.
fn isPrime(n: int): int {
    if (n < 2) {
        return 0;
    }
    var d = 2;
    while (d * d <= n) {
        if (n % d == 0) {
            return 0;
        }
        d = d + 1;
    }
    return 1;
}

fn main() {
    var limit = read();
    var count = 0;
    var k = 2;
    while (k < limit) {
        if (isPrime(k)) {
            print(k);
            count = count + 1;
        }
        k = k + 1;
    }
    print(count);
}
