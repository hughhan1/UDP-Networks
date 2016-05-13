# UDP Networks #

We learned a lot about different types of data transfer protocols in the [Computer
Communications & Networks](http://www.inf.ed.ac.uk/teaching/courses/comn/) course at the
[University of Edinburgh](http://www.ed.ac.uk). This repository contains Java
implementations of those discussed protocols. Each protocol implementation contains a
sender side and a receiver side, which work together to transfer any type of general
data file.

The four protocols are:

1. basic framework
2. stop-and-wait
3. go-back-n
4. selective repeat

The implementations for each of these protocols use data packets with a maximum payload
of 1 kilobyte (1024 bytes), with 3-byte headers, with 2 bytes representing a 16-bit
message containing the packet sequence number and and 1 byte representing an end-of-file
flag.

### Usage ###

In this sender-receiver implementation, the receiver is the listener. With that in mind,
the receiver must always be run before the sender is run. This is especially important
in the basic framework, since it uses unreliable data transfer. In the other
implementations, the sender will just stall until it finds a listener to connect to.

First, clone this repository and compile all of the Java files.
```
git clone https://github.com/hughhan1/Networks.git
cd Networks-master
javac *.java
```

##### Basic Framework #####
```
java BasicReceiver <portnumber> <filename>
java BasicSender localhost <portnumber> <filename>
```

##### Stop-And-Wait #####
```
java StopAndWaitReceiver <portnumber> <filename>
java StopAndWaitSender localhost <portnumber> <filename> <retrytimeout>
```

##### Go-Back-N #####
```
java GoBackNReceiver <portnumber> <filename> <windowsize>
java GoBackNSender localhost <portnumber> <filename> <retrytimeout> <windowsize>
```

##### Selective Repeat #####
```
java SelectiveRepeatReceiver <portnumber> <filename> <windowsize>
java SelectiveRepeatSender localhost <portnumber> <filename> <retrytimeout> <windowsize>
```

#### Usage Notes ####
For each of the protocols, ```localhost``` is the hostname used for the Inet Address.
However, this does not have to be the case, although it does make everything a lot
simpler!
