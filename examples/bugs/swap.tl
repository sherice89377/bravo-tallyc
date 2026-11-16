// Reported by a student: values change after a swap and a call.
fn area(w: int, h: int): int {
    return w * h;
}

fn main() {
    var left = read();
    var right = read();
    var tmp = left;
    left = right;
    right = tmp;
    var a = area(left, right);
    print(left);
    print(right);
    print(a);
    print(left + right);
}
