Downloads Directory
============

Place GPDB RPM (for Centos / RedHat) or GPDB DEB (for Ubuntu) packages in this directory.
They will be available to development Docker scripts that will install GPDB from these artifacts
inside the docker container.

PLEASE DO NOT check these artifacts into this Git repository !!!

For example, one of the following artifacts should be used for GPDB 6.6:

```
apache-cloudberry-db-incubating-1.0.0-1.el9.x86_64.rpm
```

You should use only the artifact for the operating system that corresponds to the Docker image you want to use.