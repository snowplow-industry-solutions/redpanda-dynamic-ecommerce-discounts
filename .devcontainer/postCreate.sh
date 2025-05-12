#!/usr/bin/env bash

# tag::system-packages[]
echo Installing system packages...
sudo apt-get update -y

echo Installing/ configuring tmux...
sudo apt-get install -y tmux
git clone https://github.com/tmux-plugins/tpm ~/.tmux/plugins/tpm
cp .devcontainer/.tmux.conf ~/
# end::system-packages[]

# tag::java-install[]
echo Installing Java 21 via SDKMAN...
curl -s "https://get.sdkman.io" | bash
source "/usr/local/sdkman/bin/sdkman-init.sh"
yes | sdk install java 21.0.7-tem
# end::java-install[]

# tag::node-install[]
echo Installing Node.js LTS...
export NVM_DIR=/usr/local/share/nvm
source $NVM_DIR/nvm.sh
nvm install --lts
nvm alias default 'lts/*'
nvm use default
# end::node-install[]

cd /tmp

# tag::yq-install[]
echo Installing yq...
VERSION=v4.45.2
BINARY=yq_linux_amd64
wget https://github.com/mikefarah/yq/releases/download/${VERSION}/${BINARY}.tar.gz -O - |
  tar xz && sudo mv ${BINARY} /usr/local/bin/yq
# end::yq-install[]

# tag::docker-asciidoctor-builder-install[]
echo Installing Docker Asciidoctor Builder...
git clone https://github.com/paulojeronimo/docker-asciidoctor-builder.git
./docker-asciidoctor-builder/install.sh
# end::docker-asciidoctor-builder-install[]

cd $OLDPWD

# tag::functions-install[]
cmd='source scripts/misc/setup.sh'
echo Calling \'$cmd\'...
$cmd
# end::functions-install[]

echo Setup completed successfully!...
