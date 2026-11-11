"""Read-only, bounded gzip NBT reader for validation artifacts. JSON is not a binary schema validator."""
import gzip
import json
import struct
import sys
from pathlib import Path


def load(path, maximum=4 * 1024 * 1024):
    with gzip.open(path, "rb") as source:
        data = source.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError("NBT exceeds validation read budget")
    offset = 0

    def read(size):
        nonlocal offset
        if size < 0 or offset + size > len(data):
            raise ValueError("Truncated NBT")
        result = data[offset:offset + size]
        offset += size
        return result

    def number(fmt):
        return struct.unpack(">" + fmt, read(struct.calcsize(">" + fmt)))[0]

    def string():
        # Java modified UTF-8 represents NUL specially and supplementary characters as surrogate pairs.
        text = read(number("H")).replace(b"\xc0\x80", b"\0").decode("utf-8", "surrogatepass")
        return text.encode("utf-16", "surrogatepass").decode("utf-16", "surrogatepass")

    def payload(kind, depth=0):
        if depth > 128:
            raise ValueError("NBT nesting exceeds validation budget")
        if 1 <= kind <= 6:
            return number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 8:
            return string()
        if kind == 10:
            result = {}
            while True:
                child = number("B")
                if child == 0:
                    return result
                name = string()
                if name in result:
                    raise ValueError("Duplicate NBT compound key")
                result[name] = payload(child, depth + 1)
        if kind in (7, 9, 11, 12):
            child = number("B") if kind == 9 else None
            count = number("i")
            if count < 0 or count > 1_000_000:
                raise ValueError("NBT collection exceeds validation budget")
            return [payload(child, depth + 1) if kind == 9 else number({7: "b", 11: "i", 12: "q"}[kind]) for _ in range(count)]
        raise ValueError("Unsupported NBT type " + str(kind))

    kind = number("B")
    string()
    value = payload(kind)
    if offset != len(data):
        raise ValueError("Trailing NBT bytes")
    return value


if __name__ == "__main__":
    value = load(Path(sys.argv[1]))
    for key in sys.argv[2:]:
        value = value[key]
    print(json.dumps(value, ensure_ascii=True))
