package baaahs

import baaahs.SheepModel.Panel
import baaahs.shaders.CompositorShader
import baaahs.shaders.PixelShader
import baaahs.shaders.SineWaveShader
import baaahs.shaders.SolidShader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.jvm.Synchronized

class Pinky(
    val sheepModel: SheepModel,
    val showMetas: List<ShowMeta>,
    val network: Network,
    val dmxUniverse: Dmx.Universe,
    val display: PinkyDisplay
) : Network.Listener {
    private lateinit var link: Network.Link
    private val brains: MutableMap<Network.Address, RemoteBrain> = mutableMapOf()
    private val beatProvider = BeatProvider(120.0f)
    private var mapperIsRunning = false
    private var brainsChanged: Boolean = true

    suspend fun run() {
        GlobalScope.launch { beatProvider.run() }

        link = network.link()
        link.listen(Ports.PINKY, this)

        display.listShows(showMetas)

        var showRunner = ShowRunner(display, brains.values.toList(), dmxUniverse)
        val prevSelectedShow = display.selectedShow
        var currentShowMeta = prevSelectedShow ?: showMetas.random()!!
        val buildShow = { currentShowMeta.createShow(sheepModel, showRunner) }
        var show = buildShow()

        while (true) {
            if (!mapperIsRunning) {
                if (brainsChanged || display.selectedShow != currentShowMeta) {
                    currentShowMeta = prevSelectedShow ?: showMetas.random()!!
                    showRunner = ShowRunner(display, brains.values.toList(), dmxUniverse)
                    show = buildShow()
                    brainsChanged = false
                }

                show.nextFrame()

                // send shader buffers out to brains
                showRunner.send(link)

//                    show!!.nextFrame(display.color, beatProvider.beat, brains, link)
            }
            delay(50)
        }
    }

    override fun receive(fromAddress: Network.Address, bytes: ByteArray) {
        val message = parse(bytes)
        when (message) {
            is BrainHelloMessage -> {
                foundBrain(RemoteBrain(fromAddress, message.panelName))
            }

            is MapperHelloMessage -> {
                mapperIsRunning = message.isRunning
            }
        }

    }

    @Synchronized
    private fun foundBrain(remoteBrain: RemoteBrain) {
        brains.put(remoteBrain.address, remoteBrain)
        display.brainCount = brains.size

        brainsChanged = true
    }

    inner class BeatProvider(val bpm: Float) {
        var startTimeMillis = 0L
        var beat = 0
        var beatsPerMeasure = 4

        suspend fun run() {
            startTimeMillis = getTimeMillis()

            while (true) {
                display.beat = beat

                val offsetMillis = getTimeMillis() - startTimeMillis
                val millisPerBeat = (1000 / (bpm / 60)).toLong()
                val delayTimeMillis = millisPerBeat - offsetMillis % millisPerBeat
                delay(delayTimeMillis)
                beat = (beat + 1) % beatsPerMeasure
            }
        }
    }
}

class ShowRunner(
    private val pinkyDisplay: PinkyDisplay,
    private val brains: List<RemoteBrain>,
    private val dmxUniverse: Dmx.Universe
) {
    private val shaders: MutableMap<Shader, MutableList<RemoteBrain>> = hashMapOf()

    fun getColorPicker(): ColorPicker = ColorPicker(pinkyDisplay)

    private fun recordShader(panel: Panel, shader: Shader) {
        shaders[shader] = brains.filter { it.panelName == panel.name }.toMutableList()
    }

    fun getSolidShader(panel: Panel): SolidShader = SolidShader().also { recordShader(panel, it) }

    fun getPixelShader(panel: Panel): PixelShader = PixelShader().also { recordShader(panel, it) }

    fun getSineWaveShader(panel: Panel): SineWaveShader = SineWaveShader().also { recordShader(panel, it) }

    fun getCompositorShader(panel: Panel, shaderA: Shader, shaderB: Shader): CompositorShader {
        val shaderABrains = shaders[shaderA]!!
        val shaderBBrains = shaders[shaderB]!!
        shaders.remove(shaderA)
        shaders.remove(shaderB)
        return CompositorShader(shaderA, shaderB).also { recordShader(panel, it) }
    }

    fun getDmxBuffer(baseChannel: Int, channelCount: Int) =
        dmxUniverse.writer(baseChannel, channelCount)

    fun getMovingHead(movingHead: SheepModel.MovingHead): Shenzarpy {
        val baseChannel = Config.DMX_DEVICES[movingHead.name]!!
        return Shenzarpy(getDmxBuffer(baseChannel, 16))
    }

    fun send(link: Network.Link) {
        shaders.forEach { (shader, remoteBrains) ->
            remoteBrains.forEach { remoteBrain ->
                link.send(remoteBrain.address, Ports.BRAIN, BrainShaderMessage(shader))
            }
        }

        dmxUniverse.sendFrame()
    }
}

class ColorPicker(private val pinkyDisplay: PinkyDisplay) {
    val color: Color get() = pinkyDisplay.color ?: Color.WHITE
}

class RemoteBrain(val address: Network.Address, val panelName: String)
