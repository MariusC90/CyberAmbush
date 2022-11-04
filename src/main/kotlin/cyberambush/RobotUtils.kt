package cyberambush

import robocode.Rules
import robocode.util.Utils
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.*

class RobotUtils {
    private var battleFieldWidth = 0.0
    private var battleFieldHeight = 0.0
    private var battleField: Rectangle2D.Double? = null
    private lateinit var corners: Array<Point2D.Double?>
    private var center: Point2D.Double? = null

    fun setDimensions(battleFieldWidth: Double, battleFieldHeight: Double) {
        this.battleFieldWidth = battleFieldWidth
        this.battleFieldHeight = battleFieldHeight
        //rectangle for field
        battleField = Rectangle2D.Double(
            BOT_HALF_WIDTH.toDouble(),
            BOT_HALF_WIDTH.toDouble(),
            battleFieldWidth - BOT_WIDTH,
            battleFieldHeight - BOT_WIDTH
        )
        corners = arrayOfNulls(4)
        for (i in 0..3) {
            corners[i] = Point2D.Double()
            corners[i]!!.setLocation(
                BOT_HALF_WIDTH + i % 2 * (battleFieldWidth - BOT_WIDTH),
                (if (i > 1) (battleFieldHeight - BOT_HALF_WIDTH) * 1.0 else (BOT_HALF_WIDTH * 1.0))
            )
        }
        center = Point2D.Double()
        center!!.setLocation(battleFieldWidth / 2, battleFieldHeight / 2)
    }

    //circle opponent
    fun circle(
        sourcePoint: Point2D.Double,
        circleDirection: Int,
        enemyLocation: Point2D.Double,
        desiredDistance: Double,
        enemyBulletPower: Double
    ): Point2D.Double {
        val factor = limit(SMALLEST_FACTOR, desiredDistance / sourcePoint.distance(enemyLocation), BIGGEST_FACTOR)
        var wallStick = sourcePoint.distance(enemyLocation) * sin(maximumEscapeAngle(enemyBulletPower))
        wallStick = limit(MIN_WALL_STICK, wallStick, MAX_WALL_STICK)
        val p = project(
            sourcePoint,
            absoluteBearing(sourcePoint, enemyLocation) - factor * circleDirection * Math.PI / 2,
            wallStick
        )
        return wallSmoothing(sourcePoint, p, circleDirection, wallStick)
    }

    //https://robowiki.net/wiki/Wall_Smoothing/Implementations
    //Fast Exact Wall Smoothing (by Cb)
    private fun wallSmoothing(
        location: Point2D.Double,
        destination: Point2D.Double,
        circleDirection: Int,
        wallStick: Double
    ): Point2D.Double {
        val point = Point2D.Double(destination.x, destination.y)
        var i = 0
        while (!battleField!!.contains(point) && i < 4) {
            if (point.x < WALL_BORDER) {
                point.x = WALL_BORDER
                val a = location.x - WALL_BORDER
                point.y = location.y + circleDirection * sqrt(wallStick * wallStick - a * a)
            } else if (point.y > battleFieldHeight - WALL_BORDER) {
                point.y = battleFieldHeight - WALL_BORDER
                val a = battleFieldHeight - WALL_BORDER - location.y
                point.x = location.x + circleDirection * sqrt(wallStick * wallStick - a * a)
            } else if (point.x > battleFieldWidth - WALL_BORDER) {
                point.x = battleFieldWidth - WALL_BORDER
                val a = battleFieldWidth - WALL_BORDER - location.x
                point.y = location.y - circleDirection * sqrt(wallStick * wallStick - a * a)
            } else if (point.y < WALL_BORDER) {
                point.y = WALL_BORDER
                val a = location.y - WALL_BORDER
                point.x = location.x - circleDirection * sqrt(wallStick * wallStick - a * a)
            }
            i++
        }
        return point
    }

    fun goTo(state: MovementState, destination: Point2D.Double): MovementParams {
        val movementParams = MovementParams()
        val x = destination.x - state.location.x
        val y = destination.y - state.location.y
        val angleToTarget = atan2(x, y)
        val targetAngle = Utils.normalRelativeAngle(angleToTarget - state.heading)

        val distance = hypot(x, y)
        val turnAngle = atan(tan(targetAngle))
        if (distance > 1) {
            movementParams.turnAngle = turnAngle
        }
        var velocity = min(8.0, cos(turnAngle) * 10) + 1
        var w: Point2D.Double
        do {
            velocity--
            val len = velocity * velocity / 2 + velocity
            w = project(state.location, state.heading, len * if (targetAngle == turnAngle) 1 else -1)
        } while (velocity > 0 && !battleField!!.contains(w))
        movementParams.maxVelocity = velocity
        if (targetAngle == turnAngle) {
            movementParams.distance = distance
        } else {
            movementParams.distance = -distance
        }
        if (distance < 15 && abs(turnAngle) > 0.1) {
            movementParams.maxVelocity = 0.0
        }
        return movementParams
    }

    fun contains(p: Point2D.Double, battleBorder: Double): Boolean {
        return p.x >= battleBorder && p.y >= battleBorder && p.x <= battleFieldWidth - battleBorder && p.y <= battleFieldHeight - battleBorder
    }

    companion object {
        private const val BOT_WIDTH = 36
        const val BOT_HALF_WIDTH = 18
        private const val WALL_BORDER = BOT_HALF_WIDTH + 1.5
        private const val SMALLEST_FACTOR = 0.95
        private const val BIGGEST_FACTOR = 1.7
        private const val MIN_WALL_STICK = 100.0
        private const val MAX_WALL_STICK = 120.0

        fun project(sourceLocation: Point2D.Double, angle: Double, length: Double): Point2D.Double {
            return Point2D.Double(
                sourceLocation.getX() + sin(angle) * length,
                sourceLocation.getY() + cos(angle) * length
            )
        }

        @JvmStatic
        fun absoluteBearing(source: Point2D.Double, target: Point2D.Double): Double {
            return atan2(target.getX() - source.getX(), target.getY() - source.getY())
        }

        @JvmStatic
        fun limit(min: Double, value: Double, max: Double): Double {
            return max(min, min(value, max))
        }

        //When firing, the Maximum Escape Angle (MEA) is the largest angle offset from zero
        // (i.e., Head-On Targeting) that could possibly hit an enemy bot
        // With a maximum Robot velocity of 8.0, a theoretical Maximum Escape Angle would be asin(8.0/Vb).
        // Note that the actual maximum depends on the enemy's current heading, speed, and Wall Distance.
        @JvmStatic
        fun maximumEscapeAngle(bulletPower: Double): Double {
            return asin(8.0 / Rules.getBulletSpeed(bulletPower))
        }
    }
}