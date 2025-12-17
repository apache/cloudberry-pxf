case "$(uname -m)" in
  aarch64|arm64) JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-arm64} ;;
  x86_64|amd64)  JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64} ;;
  *)             JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64} ;;
esac
export PATH=$JAVA_HOME/bin:$PATH
export GPHOME=/usr/local/cloudberry-db
source /usr/local/cloudberry-db/cloudberry-env.sh
export PATH=$GPHOME/bin:$PATH

sudo apt update
sudo apt install -y openjdk-11-jdk maven

cd /home/gpadmin/workspace/cloudberry-pxf

# mirror
# If the download fails, you can uncomment the line to switch to another mirror address.
# Configure Gradle to use Aliyun mirror
# mkdir -p ~/.gradle
# cat > ~/.gradle/init.gradle <<'EOF'
# allprojects {
#     repositories {
#         maven { url 'https://maven.aliyun.com/repository/public/' }
#         maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
#         mavenCentral()
#     }
#     buildscript {
#         repositories {
#             maven { url 'https://maven.aliyun.com/repository/public/' }
#             maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
#             mavenCentral()
#         }
#     }
# }
# EOF

# Set Go environment
export GOPATH=$HOME/go
export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin
# mirror
# If the download fails, you can uncomment the line to switch to another mirror address.
# export GOPROXY=https://goproxy.cn,direct
mkdir -p $GOPATH
export PXF_HOME=/usr/local/pxf
sudo mkdir -p "$PXF_HOME"
sudo chown -R gpadmin:gpadmin "$PXF_HOME"

# Build all PXF components
make all

# Install PXF
make install

# Set up PXF environment

export PXF_BASE=$HOME/pxf-base
export PATH=$PXF_HOME/bin:$PATH
rm -rf "$PXF_BASE"
mkdir -p "$PXF_BASE"

# Initialize PXF
pxf prepare
pxf start

# Verify PXF is running
pxf status
