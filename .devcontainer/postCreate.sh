#!/usr/bin/env bash

# tag::system-packages[]
echo Installing system packages...
sudo apt-get update -y

echo Installing/ configuring tmux...
sudo apt-get install -y tmux
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

cmd='source scripts/misc/functions.sh'
echo Calling \'$cmd\'...
$cmd

echo Setup completed successfully!...
