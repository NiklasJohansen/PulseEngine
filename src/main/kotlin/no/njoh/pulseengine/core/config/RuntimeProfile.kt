package no.njoh.pulseengine.core.config

/**
 * Declares the engine subsystems and graphics contract required at startup.
 * The profile is selected explicitly when launching the game and is never inferred from the host system.
 * Graphical profiles are validated before renderer assets are initialized.
 */
enum class RuntimeProfile
{
    /**
     * Runs without window, graphics, audio, input, or asset subsystems and does not create an
     * OpenGL context.
     */
    HEADLESS,

    /**
     * Enables all subsystems and the base graphics feature set.
     *
     * This profile requires OpenGL 4.1 core and GLSL 4.10. It is supported on Windows, Linux,
     * and macOS and includes the built-in 2D renderer. Games may provide custom 3D rendering
     * within the OpenGL 4.1 contract, but the built-in 3D renderer is not available.
     */
    BASE_GRAPHICS,

    /**
     * Enables all subsystems and the complete built-in graphics feature set.
     *
     * This profile requires OpenGL 4.4 core and GLSL 4.40 with persistent mapped buffers,
     * compute shaders, and shader storage buffers. It is supported on Windows and Linux, but not
     * macOS, whose system OpenGL implementation is limited to version 4.1. Both built-in 2D and
     * 3D renderers are available.
     */
    FULL_GRAPHICS
}
