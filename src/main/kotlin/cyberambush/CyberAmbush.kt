package cyberambush

import robocode.*
import robocode.util.Utils
import java.awt.Color
import java.awt.geom.Point2D
import kotlin.math.*

class CyberAmbush : AdvancedRobot() {
    private var localTime: Long = 0

    private var circleDirection = 1
    private var myState: MovementState? = null

    private var myEnergy = 0.0
    private var myBulletPower = 0.0

    private var opponentState: MovementState? = null
    private var opponentEnergy = 100.0
    private var opponentBulletPower = 2.0

    private var destination: Point2D.Double = Point2D.Double()
    private var target: Point2D.Double = Point2D.Double()


    private val possibleDestinations = ArrayList<Point2D.Double>()

    private fun initialize() {
        setColors(
            Color(76, 17, 52),
            Color(119, 255, 0),
            Color(118, 1, 136)
        )
        battleField = RobotUtils()
        battleField.setDimensions(battleFieldWidth, battleFieldHeight)
        initialized = true
    }

    override fun run() {
        isAdjustRadarForGunTurn = true
        isAdjustGunForRobotTurn = true

        while (true) {
            radar()
            movement()
            gun()
            execute()
        }
    }

    private fun radar() {
        //try to track opponent
        if (opponentState == null) {
            //rotate if no state for opponent
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY)
        } else {
            //track opponent
            val angle = RobotUtils.absoluteBearing(myState!!.location, opponentState!!.location)
            val maxDistance = ((myState!!.time - opponentState!!.time) * 8).toDouble()
            val extraTurn = atan(maxDistance / myState!!.location.distance(opponentState!!.location))
            if (Utils.normalRelativeAngle(angle - radarHeadingRadians) < 0) {
                setTurnRadarRightRadians(Utils.normalRelativeAngle(angle - extraTurn - radarHeadingRadians))
            } else {
                setTurnRadarRightRadians(Utils.normalRelativeAngle(angle + extraTurn - radarHeadingRadians))
            }
        }
    }

    private fun movement() {
        possibleDestinations.clear()
        if (opponentState != null) {
            destination = battleField.circle(
                myState!!.location, circleDirection, opponentState!!.location, desiredDistance, opponentBulletPower
            )
            val b = battleField.circle(
                myState!!.location, -circleDirection, opponentState!!.location, desiredDistance, opponentBulletPower
            )
            if (opponentState!!.location.distance(destination) < myState!!.location.distance(destination) && opponentState!!.location.distance(
                    b
                ) > myState!!.location.distance(b)
            ) {
                circleDirection = -circleDirection
                destination = b
            }
        } else {
            destination.setLocation(myState!!.location.x, myState!!.location.y)
        }
        val commands = battleField.goTo(myState!!, destination)
        setTurnRightRadians(commands.turnAngle)
        setMaxVelocity(commands.maxVelocity)
        setAhead(commands.distance)
    }

    private val desiredDistance: Double
        get() = RobotUtils.limit(350.0, myState!!.location.distance(opponentState!!.location) + 80, 1000.0)

    private fun gun() {
        if (opponentState != null) {
            calculateBulletPower()
            target = opponentState!!.location
            val targetAngle = RobotUtils.absoluteBearing(myState!!.location, target)
            val angleTolerance = atan(14 / myState!!.location.distance(target))
            val randomOffset = (0.5 - Math.random()) * atan(3 / myState!!.location.distance(target))

            setTurnGunRightRadians(Utils.normalRelativeAngle(targetAngle + randomOffset - gunHeadingRadians))
            if (myEnergy > 0.101 && abs(gunTurnRemainingRadians) < angleTolerance) {
                val bullet = setFireBullet(myBulletPower)
                if (bullet != null) {
                    myBulletPowerFired += myBulletPower
                }
            }
        }
    }

    private fun calculateBulletPower() {
        val distance = myState!!.location.distance(opponentState!!.location)
        if (distance < 140) {
            myBulletPower = myEnergy
        } else {
            myBulletPower = 1.99
            val myHitRate = myBulletPowerHit / myBulletPowerFired
            if (myBulletPowerFired > 20 && myHitRate > 0.25) {
                myBulletPower = 2.49
                if (myHitRate > 0.33) {
                    myBulletPower = 2.99
                }
            }
            if (distance > 325 && myEnergy < 63) {
                if (distance > 600 && (myEnergy < 20 || myEnergy - 10 < opponentEnergy)) {
                    myBulletPower = 0.1
                } else {
                    val powerDownPoint = RobotUtils.limit(35.0, 63 + 4 * (opponentEnergy - myEnergy), 63.0)
                    if (myEnergy < powerDownPoint) {
                        val v = myEnergy / powerDownPoint
                        myBulletPower = min(myBulletPower, v * v * v * 1.99)
                    }
                    if (myEnergy - 25 < opponentEnergy) {
                        myBulletPower = min(myBulletPower, opponentBulletPower * 0.9)
                    }
                }
            }
            myBulletPower = min(myBulletPower, opponentEnergy / 4)
            myBulletPower = min(myBulletPower, myEnergy)
        }
        myBulletPower = RobotUtils.limit(0.1, myBulletPower, 2.999)
    }

    override fun onStatus(e: StatusEvent) {
        if (!initialized) {
            initialize()
        }
        localTime = e.time
        myEnergy = e.status.energy

        myState =
            MovementState(localTime, Point2D.Double(e.status.x, e.status.y), e.status.headingRadians)
    }

    override fun onScannedRobot(e: ScannedRobotEvent) {
        opponentName = e.name
        //calc opponent location
        val location = RobotUtils.project(myState!!.location, e.bearingRadians + myState!!.heading, e.distance)
        val energyLoss = opponentEnergy - e.energy

        //if energy loss is between 1 and 3, we have a bullet fired
        if (energyLoss > 0.099 && energyLoss < 3.01) {
            //opponent fires gun
            opponentBulletPower = energyLoss
        }

        opponentEnergy = e.energy
        opponentState = MovementState(localTime, location, e.headingRadians)
    }

    override fun onBulletHit(e: BulletHitEvent) {
        myBulletPowerHit += e.bullet.power
        opponentEnergy -= Rules.getBulletDamage(e.bullet.power)
    }

    override fun onHitByBullet(e: HitByBulletEvent) {
        opponentEnergy += Rules.getBulletHitBonus(e.bullet.power)
    }

    override fun onRobotDeath(e: RobotDeathEvent) {
        opponentState = null
    }

    companion object {
        private var initialized = false
        private lateinit var battleField: RobotUtils
        private var myBulletPowerFired = 0.0
        private var myBulletPowerHit = 0.0
        private lateinit var opponentName: String
    }
}