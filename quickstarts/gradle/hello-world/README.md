# JKube Hello World Sample

This is a demo project for getting started with Eclipse JKube. It just prints "Hello World" on command
line and exits. We would be using Eclipse JKube for building a docker image and deploying to Kubernetes
in single command.

## Creating Docker Image
You can create docker image for this simple java exec application by running gradle kubernetes build task.
This will perform a Docker build by default:
```bash
hello-world : $ gradle k8sBuild

> Task :k8sBuild
k8s: Running in Kubernetes mode
k8s: Building Docker image in Kubernetes mode
k8s: Pulling from library/openjdk
k8s: Digest: sha256:70a40f80e1cff29b88c529dc0c0444a21c2616d7f5a4ce65088585e1e71f07b9
k8s: Status: Image is up to date for openjdk:latest
k8s: Pulled openjdk:latest in 4 seconds 
k8s: [helloworld-java:latest] "hello-word": Created docker-build.tar in 3 milliseconds
k8s: [helloworld-java:latest] "hello-word": Built image sha256:485b5
k8s: [helloworld-java:latest] "hello-word": Removed old image sha256:d0040

BUILD SUCCESSFUL in 6s
1 actionable task: 1 executed
hello-world : $ docker run 485b5
Hello World!
hello-world : $ 
```
