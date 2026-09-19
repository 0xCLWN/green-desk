package scan

import (
	"encoding/binary"
	"net"
	"sync"
	"time"
)

// ScanCIDR scans all IPs in the given CIDR concurrently.
// Returns total (number of IPs to scan), a results channel (feasible hosts only),
// and a progress channel (one tick per IP checked, feasible or not).
// Both channels are closed when scanning is done.
func ScanCIDR(cidr string, port, workers int, timeout time.Duration) (total int, out <-chan *Result, prog <-chan struct{}) {
	outCh := make(chan *Result, 64)
	progCh := make(chan struct{}, 256)

	ips, err := expandCIDR(cidr)
	if err != nil {
		close(outCh)
		close(progCh)
		return 0, outCh, progCh
	}

	sem := make(chan struct{}, workers)
	var wg sync.WaitGroup

	go func() {
		for _, ip := range ips {
			ip := ip
			sem <- struct{}{}
			wg.Add(1)
			go func() {
				defer func() {
					<-sem
					wg.Done()
				}()
				r := ScanIP(ip, port, timeout)
				progCh <- struct{}{}
				if r != nil && r.Feasible {
					outCh <- r
				}
			}()
		}
		wg.Wait()
		close(outCh)
		close(progCh)
	}()

	return len(ips), outCh, progCh
}

// expandCIDR returns all host IPs in a CIDR (excludes network + broadcast).
func expandCIDR(cidr string) ([]net.IP, error) {
	_, network, err := net.ParseCIDR(cidr)
	if err != nil {
		return nil, err
	}

	var ips []net.IP
	base := binary.BigEndian.Uint32(network.IP.To4())
	mask := binary.BigEndian.Uint32([]byte(network.Mask))
	start := base & mask
	end := start | ^mask

	for n := start + 1; n < end; n++ {
		b := make([]byte, 4)
		binary.BigEndian.PutUint32(b, n)
		ips = append(ips, net.IP(b))
	}
	return ips, nil
}

// GuessCIDR returns a /24 CIDR from a single IP string (e.g. "1.2.3.4" → "1.2.3.0/24").
func GuessCIDR(ipStr string) string {
	ip := net.ParseIP(ipStr)
	if ip == nil {
		return ""
	}
	ip = ip.To4()
	if ip == nil {
		return ""
	}
	return net.IP{ip[0], ip[1], ip[2], 0}.String() + "/24"
}
