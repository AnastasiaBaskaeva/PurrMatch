FROM --platform=$BUILDPLATFORM golang:1.27.3-bookworm@sha256:b08bc99468058b6d8a0ada17fa54996b910d5e82a7e95bee0372e3bd98415258 AS build
WORKDIR /src
COPY go.* ./
COPY cmd ./cmd
ARG TARGETOS
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH GOTOOLCHAIN=local go build -mod=readonly -trimpath -ldflags="-s -w" -o /out/purrmatch ./cmd/purrmatch

FROM scratch
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /out/purrmatch /purrmatch
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/purrmatch"]
