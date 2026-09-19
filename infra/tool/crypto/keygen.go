package crypto

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"

	"golang.org/x/crypto/curve25519"
)

// GenX25519 generates a fresh Reality keypair.
// Returns (privateKeyB64, publicKeyB64, error).
func GenX25519() (string, string, error) {
	var private [32]byte
	if _, err := rand.Read(private[:]); err != nil {
		return "", "", err
	}
	// Clamp as per RFC 7748
	private[0] &= 248
	private[31] &= 127
	private[31] |= 64

	public, err := curve25519.X25519(private[:], curve25519.Basepoint)
	if err != nil {
		return "", "", err
	}
	enc := base64.RawURLEncoding
	return enc.EncodeToString(private[:]), enc.EncodeToString(public), nil
}

// GenUUID returns a random UUID v4 string.
func GenUUID() string {
	var b [16]byte
	rand.Read(b[:])
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

// GenShortID returns 4 random bytes as a lowercase hex string.
func GenShortID() string {
	var b [4]byte
	rand.Read(b[:])
	return hex.EncodeToString(b[:])
}

// GenPort returns a random non-privileged port in [10000, 65000).
func GenPort() int {
	var b [2]byte
	rand.Read(b[:])
	return 10000 + (int(b[0])<<8|int(b[1]))%55000
}

// GenPassword returns a cryptographically random alphanumeric password.
func GenPassword(length int) string {
	const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, length)
	rand.Read(b)
	for i, v := range b {
		b[i] = chars[int(v)%len(chars)]
	}
	return string(b)
}
