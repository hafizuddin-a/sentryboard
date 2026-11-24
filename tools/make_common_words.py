#!/usr/bin/env python3
"""Builds app/src/main/res/raw/common_words.bin, the Bloom filter of English words that
CommonWords.java reads. It is used to tell an ordinary word (never blocked by a typo rule) from a
mistyped one, so a false "yes, it is a word" only ever means a typo is missed, never a wrong block.

Source: the system word list (/usr/share/dict/words, Webster's Second International), lower-case
entries only so proper names are left out. Inflections (-s, -ed, -ing, ...) are not stored; the Java
side strips them before looking up.

File format: 1 byte k (hash count), 4 bytes m (bit count, big endian), then ceil(m/8) bytes of bits,
least significant bit first within each byte. Hashing: FNV-1a 64 over the ASCII bytes; h1 = low 32
bits, h2 = high 32 bits | 1; bit i = (h1 + i*h2) mod m.

Run:  python3 tools/make_common_words.py [word list] [output]
"""
import math
import sys

SOURCE = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/dict/words"
OUTPUT = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/res/raw/common_words.bin"
FALSE_POSITIVE_RATE = 0.04
MASK = (1 << 64) - 1


def fnv(word):
    h = 0xCBF29CE484222325
    for b in word.encode("ascii"):
        h = ((h ^ b) * 0x100000001B3) & MASK
    return h


def positions(word, k, m):
    h = fnv(word)
    h1 = h & 0xFFFFFFFF
    h2 = (h >> 32) | 1
    return [(h1 + i * h2) % m for i in range(k)]


def main():
    words = set()
    with open(SOURCE, encoding="latin-1") as f:
        for line in f:
            w = line.strip()
            if 2 <= len(w) <= 24 and w.isascii() and w.isalpha() and w.islower():
                words.add(w)
    n = len(words)
    m = int(math.ceil(-n * math.log(FALSE_POSITIVE_RATE) / (math.log(2) ** 2)))
    k = max(1, int(round(m / n * math.log(2))))
    bits = bytearray((m + 7) // 8)
    for w in words:
        for p in positions(w, k, m):
            bits[p >> 3] |= 1 << (p & 7)
    with open(OUTPUT, "wb") as out:
        out.write(bytes([k]) + m.to_bytes(4, "big") + bytes(bits))
    print("words %d, bits %d, hashes %d, file %d bytes" % (n, m, k, 5 + len(bits)))


main()
