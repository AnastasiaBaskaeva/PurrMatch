package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	addr := os.Getenv("HTTP_ADDR")
	if addr == "" {
		addr = ":8080"
	}

	server := &http.Server{
		Addr:              addr,
		Handler:           handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       time.Minute,
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	listener, err := net.Listen("tcp", server.Addr)
	if err != nil {
		slog.Error("HTTP listen failed", "addr", addr, "error", err)
		os.Exit(1)
	}
	slog.Info("HTTP server started", "addr", listener.Addr().String())
	if err := serve(ctx, server, listener); err != nil {
		slog.Error("HTTP server failed", "error", err)
		os.Exit(1)
	}
	slog.Info("HTTP server stopped")
}

func handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte("{\"status\":\"ok\"}\n"))
	})
	return mux
}

func serve(ctx context.Context, server *http.Server, listener net.Listener) error {
	defer server.Close()
	serveErrors := make(chan error, 1)
	go func() {
		serveErrors <- server.Serve(listener)
	}()

	var err error
	select {
	case err = <-serveErrors:
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := server.Shutdown(shutdownCtx); err != nil {
			return fmt.Errorf("HTTP shutdown failed: %w", err)
		}
		err = <-serveErrors
	}
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}
