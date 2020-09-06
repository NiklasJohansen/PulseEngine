package no.njoh.pulseengine.data

enum class Key(val code: Int)
{
    // Printable Keys
    SPACE(32),
    APOSTROPHE(39),
    COMMA(44),
    MINUS(45),
    PERIOD(46),
    SLASH(47),
    K_0(48),
    K_1(49),
    K_2(50),
    K_3(51),
    K_4(52),
    K_5(53),
    K_6(54),
    K_7(55),
    K_8(56),
    K_9(57),
    SEMICOLON(59),
    EQUAL(61),
    A(65),
    B(66),
    C(67),
    D(68),
    E(69),
    F(70),
    G(71),
    H(72),
    I(73),
    J(74),
    K(75),
    L(76),
    M(77),
    N(78),
    O(79),
    P(80),
    Q(81),
    R(82),
    S(83),
    T(84),
    U(85),
    V(86),
    W(87),
    X(88),
    Y(89),
    Z(90),
    LEFT_BRACKET(91),
    BACKSLASH(92),
    RIGHT_BRACKET(93),
    GRAVE_ACCENT(96),
    WORLD_1(161),
    WORLD_2(162),
    
    // Function keys
    ESCAPE(256),
    ENTER(257),
    TAB(258),
    BACKSPACE(259),
    INSERT(260),
    DELETE(261),
    RIGHT(262),
    LEFT(263),
    DOWN(264),
    UP(265),
    PAGE_UP(266),
    PAGE_DOWN(267),
    HOME(268),
    END(269),
    CAPS_LOCK(280),
    SCROLL_LOCK(281),
    NUM_LOCK(282),
    PRINT_SCREEN(283),
    PAUSE(284),
    F1(290),
    F2(291),
    F3(292),
    F4(293),
    F5(294),
    F6(295),
    F7(296),
    F8(297),
    F9(298),
    F10(299),
    F11(300),
    F12(301),
    F13(302),
    F14(303),
    F15(304),
    F16(305),
    F17(306),
    F18(307),
    F19(308),
    F20(309),
    F21(310),
    F22(311),
    F23(312),
    F24(313),
    F25(314),
    KP_0(320),
    KP_1(321),
    KP_2(322),
    KP_3(323),
    KP_4(324),
    KP_5(325),
    KP_6(326),
    KP_7(327),
    KP_8(328),
    KP_9(329),
    KP_DECIMAL(330),
    KP_DIVIDE(331),
    KP_MULTIPLY(332),
    KP_SUBTRACT(333),
    KP_ADD(334),
    KP_ENTER(335),
    KP_EQUAL(336),
    LEFT_SHIFT(340),
    LEFT_CONTROL(341),
    LEFT_ALT(342),
    LEFT_SUPER(343),
    RIGHT_SHIFT(344),
    RIGHT_CONTROL(345),
    RIGHT_ALT(346),
    RIGHT_SUPER(347),
    MENU(348),
    LAST(MENU.code),
}

enum class Mouse(val code: Int)
{
    LEFT(0),
    RIGHT(1),
    MIDDLE(2),
    BUTTON_4(3),
    BUTTON_5(4),
    BUTTON_6(5),
    BUTTON_7(6),
    BUTTON_8(7),
    BUTTON_LAST(BUTTON_8.code),
    BUTTON_LEFT(LEFT.code),
    BUTTON_RIGHT(RIGHT.code),
    BUTTON_MIDDLE(MIDDLE.code),
}

enum class Button(val code: Int)
{
    A(0),
    B(1),
    X(2),
    Y(3),
    LEFT_BUMPER(4),
    RIGHT_BUMPER(5),
    BACK(6),
    START(7),
    GUIDE(8),
    LEFT_THUMB(9),
    RIGHT_THUMB(10),
    DPAD_UP(11),
    DPAD_RIGHT(12),
    DPAD_DOWN(13),
    DPAD_LEFT(14),
    LAST(DPAD_LEFT.code),
    CROSS(A.code),
    CIRCLE(B.code),
    SQUARE(X.code),
    TRIANGLE(Y.code),
}

enum class Axis(val code: Int)
{
    LEFT_X(0),
    LEFT_Y(1),
    RIGHT_X(2),
    RIGHT_Y(3),
    LEFT_TRIGGER(4),
    RIGHT_TRIGGER(5),
}

enum class CursorType
{
    ARROW,
    HAND,
    IBEAM,
    CROSSHAIR,
    HORIZONTAL_RESIZE,
    VERTICAL_RESIZE,
    MOVE,
    TOP_LEFT_RESIZE,
    TOP_RIGHT_RESIZE,
    ROTATE
}

enum class ScreenMode
{
    WINDOWED,
    FULLSCREEN
}

enum class ShaderType
{
    VERTEX,
    FRAGMENT
}

enum class FileFormat
{
    JSON,
    BINARY
}

enum class SceneState {
    STOPPED,
    PAUSED,
    RUNNING
}