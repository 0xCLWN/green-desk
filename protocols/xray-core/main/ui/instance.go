package ui

import (
	"fmt"
	"net"
)

var instanceLock net.Listener

// acquireSingleInstance tries to bind 127.0.0.1:<port-1> as an exclusive
// instance lock.  Returns false if another instance already holds that port.
// The OS releases the lock automatically on process exit, crash, or kill —
// no stale lock files to worry about.
func acquireSingleInstance(socksPort int) bool {
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", socksPort-1))
	if err != nil {
		return false
	}
	instanceLock = ln
	return true
}

func releaseInstanceLock() {
	if instanceLock != nil {
		instanceLock.Close()
		instanceLock = nil
	}
}
