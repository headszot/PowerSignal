# PowerSignal

Have you ever wanted to remotely control your computer via Signal? Now you can!

Usage: java -jar .\PowerSignal.jar .\config.json

## Dependencies

Relies on the wonderful signal-cli project by AsamK

https://github.com/AsamK/signal-cli

## Setup

### Adding your machine as a linked device

You can setup signal-cli as a new device with a new phone number, or (preferrably) add your server as a linked device on your existing Signal account:

./signal-cli link -n <your-server-name> | qrencode -t ANSI
  
Then scan the produced QR with your mobile device to finish the linking process.

### Running the signal-cli daemon

Run signal-cli in daemon mode listening on your chosen port e.g.
  
./signal-cli -u '+<your-registered-phone-number>' daemon --socket 9999
  
All your received messages will now be synced to signal-cli.
  
### Setting up the RPC group
  
You must first create a group (or use an existing one) and get its base64 groupID. Run the signal-cli in JsonRpc mode:
  
./signal-cli -u '+<your-registered-phone-number>' JsonRpc

Send the following JSON:
  
{"id":"0","jsonrpc", "2.0","method", "listGroups"}
  
In the response, locate your desired group and extract the id. Add this string to the config.json file under the "groupID" property.
  
## Demo
  
Spawning a new powershell process and interacting with it via Signal:
  
![Powershell demo](https://github.com/headszot/PowerSignal/blob/main/demo/signal-ps-demo.png?raw=true)
