package ui

import (
	"fmt"
	"math/rand/v2"
	"net/http"
	"net/url"
	"time"
)

func newHealthClient(port int) *http.Client {
	proxyURL, _ := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", port+1))
	return &http.Client{
		Transport: &http.Transport{
			Proxy:             http.ProxyURL(proxyURL),
			DisableKeepAlives: true,
		},
		Timeout: 5 * time.Second,
	}
}

func checkUpstream(client *http.Client) bool {
	resp, err := client.Get("http://connectivitycheck.gstatic.com/generate_204")
	if err != nil {
		return false
	}
	resp.Body.Close()
	return resp.StatusCode == 204
}

// randInterval returns a normally distributed duration with mean 60 s and
// stddev 10 s, clamped to [30 s, 90 s].
func randInterval() time.Duration {
	const mean, stddev = 60.0, 10.0
	secs := mean + stddev*rand.NormFloat64()
	if secs < 30 {
		secs = 30
	} else if secs > 90 {
		secs = 90
	}
	return time.Duration(secs * float64(time.Second))
}

// startHealthWatch polls upstream at randomised intervals (normally distributed
// 30–90 s) via the local HTTP proxy.
// onDown fires on the first failed check; onUp fires when it recovers.
// Returns a stop function.
func startHealthWatch(port int, onDown, onUp func()) func() {
	client := newHealthClient(port)
	done := make(chan struct{})
	go func() {
		isDown := false
		for {
			timer := time.NewTimer(randInterval())
			select {
			case <-done:
				timer.Stop()
				return
			case <-timer.C:
				ok := checkUpstream(client)
				if !ok && !isDown {
					isDown = true
					onDown()
				} else if ok && isDown {
					isDown = false
					onUp()
				}
			}
		}
	}()
	return func() { close(done) }
}
