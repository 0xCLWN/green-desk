// Package scan implements TLS scanning for REALITY-compatible hosts.
// Scanning logic adapted from https://github.com/XTLS/RealiTLScanner (MIT).
// Extended to include certificate age.
package scan

import (
	"crypto/tls"
	"fmt"
	"net"
	"time"
)

// Result holds the scan result for a single IP.
type Result struct {
	IP       string
	Domain   string
	Issuer   string
	TLSVer   string
	CertAge  string // e.g. "142 days"
	Feasible bool
}

// ScanIP performs a TLS handshake against ip:port and returns a Result.
// Returns nil if the connection could not be established.
func ScanIP(ip net.IP, port int, timeout time.Duration) *Result {
	addr := fmt.Sprintf("%s:%d", ip.String(), port)

	dialer := &net.Dialer{Timeout: timeout}
	conn, err := dialer.Dial("tcp", addr)
	if err != nil {
		return nil
	}
	defer conn.Close()

	conn.SetDeadline(time.Now().Add(timeout))

	tlsCfg := &tls.Config{
		InsecureSkipVerify: true,
		NextProtos:         []string{"h2", "http/1.1"},
		CurvePreferences:   []tls.CurveID{tls.X25519},
		MinVersion:         tls.VersionTLS12,
		MaxVersion:         tls.VersionTLS13,
	}

	tlsConn := tls.Client(conn, tlsCfg)
	if err := tlsConn.Handshake(); err != nil {
		return nil
	}
	defer tlsConn.Close()

	state := tlsConn.ConnectionState()

	// REALITY requires TLS 1.3 + h2
	alpn := state.NegotiatedProtocol
	ver := tlsVersion(state.Version)

	var domain, issuer, certAge string
	for _, cert := range state.PeerCertificates {
		if len(cert.DNSNames) > 0 {
			domain = cert.Subject.CommonName
			if len(cert.Issuer.Organization) > 0 {
				issuer = cert.Issuer.Organization[0]
			}
			age := int(time.Since(cert.NotBefore).Hours() / 24)
			certAge = fmt.Sprintf("%d days", age)
			break
		}
	}

	feasible := state.Version == tls.VersionTLS13 &&
		alpn == "h2" &&
		domain != "" &&
		issuer != ""

	return &Result{
		IP:       ip.String(),
		Domain:   domain,
		Issuer:   issuer,
		TLSVer:   ver,
		CertAge:  certAge,
		Feasible: feasible,
	}
}

func tlsVersion(v uint16) string {
	switch v {
	case tls.VersionTLS13:
		return "1.3"
	case tls.VersionTLS12:
		return "1.2"
	default:
		return "?"
	}
}
