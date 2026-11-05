package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

func TestHandler(t *testing.T) {
	for _, test := range []struct {
		method string
		path   string
		status int
	}{
		{http.MethodGet, "/healthz", http.StatusOK},
		{http.MethodPost, "/healthz", http.StatusMethodNotAllowed},
		{http.MethodGet, "/unknown", http.StatusNotFound},
	} {
		t.Run(test.method+" "+test.path, func(t *testing.T) {
			response := httptest.NewRecorder()
			handler().ServeHTTP(response, httptest.NewRequest(test.method, test.path, nil))
			if response.Code != test.status {
				t.Fatalf("status = %d, want %d", response.Code, test.status)
			}
			if test.status == http.StatusOK {
				if got := response.Header().Get("Content-Type"); got != "application/json" {
					t.Fatalf("Content-Type = %q", got)
				}
				var body map[string]string
				if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
					t.Fatal(err)
				}
				if len(body) != 1 || body["status"] != "ok" {
					t.Fatalf("health response = %v", body)
				}
			}
		})
	}
}

func TestShutdownWaitsForActiveRequest(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()

	started := make(chan struct{})
	finishRequest := make(chan struct{})
	allowFinish := sync.OnceFunc(func() { close(finishRequest) })
	defer allowFinish()
	shutdownStarted := make(chan struct{})
	server := &http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		close(started)
		<-finishRequest
		_, _ = w.Write([]byte("finished"))
	})}
	server.RegisterOnShutdown(func() { close(shutdownStarted) })
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	served := make(chan error, 1)
	go func() { served <- serve(ctx, server, listener) }()

	client := &http.Client{Timeout: 5 * time.Second}
	defer client.CloseIdleConnections()
	requested := make(chan error, 1)
	go func() {
		response, err := client.Get("http://" + listener.Addr().String())
		if err != nil {
			requested <- err
			return
		}
		defer response.Body.Close()
		body, err := io.ReadAll(response.Body)
		if err == nil && (response.StatusCode != http.StatusOK || string(body) != "finished") {
			err = fmt.Errorf("response = %d %q", response.StatusCode, body)
		}
		requested <- err
	}()
	select {
	case <-started:
	case <-time.After(5 * time.Second):
		t.Fatal("request did not start")
	}
	cancel()
	select {
	case <-shutdownStarted:
	case <-time.After(5 * time.Second):
		t.Fatal("shutdown did not start")
	}
	select {
	case err := <-served:
		t.Fatalf("server returned before the active request completed: %v", err)
	default:
	}
	allowFinish()
	select {
	case err := <-requested:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("request did not complete")
	}
	select {
	case err := <-served:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("server did not stop")
	}
}

func TestServeReturnsListenerError(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	if err := listener.Close(); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	err = serve(ctx, &http.Server{Handler: handler()}, listener)
	if !errors.Is(err, net.ErrClosed) {
		t.Fatalf("error = %v, want closed listener", err)
	}
}
