<img alt="A Cute Chipmunk" src="https://images.pexels.com/photos/1692984/pexels-photo-1692984.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2" width="100%" height="100%">

# 🐿️ CHIPMUNK: Agile Silicon Tape-out Scaffold

CHIPMUNK is a agile framework for in-house accelerator chip prototype design. 

## Dependencies

Click the triangle (►) on the left to view the installation guide.

<details>
<summary> JDK </summary>

Before starting, please make sure you have a JDK >= 8 installed. You can install a JDK through the package manager that comes with your OS, or just download a prebuilt binaries such as [Temurin](https://adoptium.net) or [Oracle OpenJDK](https://jdk.java.net/19).

To install a JDK LTS:

```sh
# macOS with Homebrew
brew install openjdk@17
# Ubuntu
apt install default-jdk
```
</details>

<details>
<summary> Mill build tool </summary>

Mill is a powerful and easy-to-use build tool by [Haoyi Li](https://github.com/lihaoyi).

To install Mill:
```sh
# macOS with Homebrew
brew install mill
```

To install mill on other platforms, please visit [its documentation](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html).
</details>
<details>
<summary> Verilator (optional) </summary>

If you want to run the Scala-written testbench, you need to install a simulation tool, such as Verilator.

To install Verilator:
```sh
# macOS with Homebrew
brew install verilator
# Ubuntu
apt install verilator
```

To veiw the `.vcd` files generated in simulation, a waveform view tool is also required. You can use GTKWave or other commercial tools.
</details>
<details>
<summary> Intellij IDEA (optional) </summary>

Intellij IDEA is an IDE widely used in the Scala community. We strongly recommend you to use it, if you need an IDE.

Intellij IDEA is developed by JetBrains, and you can download it from [here](https://www.jetbrains.com/idea)(the free Community Edition is good enough). You also need to install its Scala plugin (when you run Intellij IDEA the first time, it will ask you about it).

Sometimes you may need to specify some paths like JDK in the IDE preferences.
</details>

## Usage

Clone this repository to your local.
```shell 
git clone git@github.com:zhutmost/chipmunk.git
cd chipmunk
rm -rf .git
```

Open a terminal in the root of your cloned repository and build. The first time it runs, the process may take some minutes to download dependencies.
```shell
mill mylib.runMain mylib.RtlEmitter
```

To open this project in an IDE (such as IDEA), please export the BSP configuration first.
```sh
mill mill.bsp.BSP/install
```

You can freely add your Chisel code in `mylib` package, and have fun!
