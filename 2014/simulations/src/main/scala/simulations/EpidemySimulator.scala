package simulations

import math.random

class EpidemySimulator extends Simulator {

  def randomBelow(i: Int) = (random * i).toInt

  protected[simulations] object SimConfig {
    val population: Int = 300
    val roomRows: Int = 8
    val roomColumns: Int = 8

    // to complete: additional parameters of simulation
    val airTraffic = false
    val reduceMobility = false
    val chosenFew = false
  }

  import SimConfig._

  // to complete: construct list of persons
  val persons: List[Person] = List.tabulate(population)((i: Int) => new Person(i))

  // initialize infected people
  persons(0).setInfected
  persons(1).setInfected
  persons(2).setInfected

  // when choseFew policy is used, initialize vaccinated people
  if (chosenFew)
    for (i <- 3 to 17) {
      persons(i).vaccinated = true
    }

  // Person Class

  class Person(val id: Int) {
    var infected = false
    var sick = false
    var immune = false
    var dead = false

    var vaccinated = false

    def moveDelay =
      if (reduceMobility)
        if (this.sick)
          (randomBelow(5) + 1) * 4
        else
          (randomBelow(5) + 1) * 2
      else
        randomBelow(5) + 1

    val sickDelay = 6; // 6 days after being infected
    val deadDelay = 8; // 8 days after being sick
    val immuneDelay = 2; // 2 days after not being dead
    val healthDelay = 2; // 2 days after being immune

    // demonstrates random number generation
    var row: Int = randomBelow(roomRows)
    var col: Int = randomBelow(roomColumns)

    //
    // to complete with simulation logic
    //

    // the person's first action inserted into agenda
    afterDelay(0)(move)

    def isRoomAvailable(row: Int, col: Int) = {
      persons.filter(pers => pers.id != this.id && pers.row == row && pers.col == col) match {
        case Nil => true
        case list => !list.exists(p => p.sick || p.dead)
      }
    }

    def move: Unit = {
      if (!this.dead) {
        if (airTraffic && randomBelow(100) < 1) {
          this.row = randomBelow(roomRows)
          this.col = randomBelow(roomColumns)
        } else {
          val neighbouringRooms = List(
            ((this.row + 1) % roomRows, this.col),
            ((this.row - 1 + roomRows) % roomRows, this.col),
            (this.row, (col + 1) % roomColumns),
            (this.row, (col - 1 + roomColumns) % roomColumns))

          val avaibleRoom = for {
            room <- neighbouringRooms if isRoomAvailable(room._1, room._2)
          } yield room

          if (avaibleRoom != Nil) {
            val destRoom = avaibleRoom(randomBelow(avaibleRoom.length))
            this.row = destRoom._1
            this.col = destRoom._2
          }
        }

        checkSurroundInfected
        afterDelay(moveDelay)(move)
      }
    }

    def setVaccinated = {
      this.vaccinated = true
    }

    def setInfected = {
      if (!this.vaccinated) {
        this.infected = true
        afterDelay(sickDelay)(setSick)
      }
    }

    def setSick = {
      this.sick = true
      afterDelay(deadDelay)(setDead)
    }

    def setDead = {
      if (!this.dead) {
        this.dead = randomBelow(4) < 1
        if (!this.dead)
          afterDelay(immuneDelay)(setImmune)
      }
    }

    def setImmune = {
      this.immune = true
      this.sick = false
      afterDelay(healthDelay)(setHealthy)
    }

    def setHealthy = {
      infected = false
      sick = false
      immune = false
      dead = false
    }

    def checkSurroundInfected() { // can be simplified

      // each infected will give a chance to the this person
      //      if (!this.infected) {
      //        for (p <- persons if p.id != this.id && p.row == this.row && p.col == this.col) {
      //          if (!this.infected && p.infected == true && randomBelow(10) < 4) setInfected
      //        }
      //      }

      // test only once even with the a lot of infected
      val t = persons.filter(p => p.id != this.id && p.infected == true && p.row == this.row && p.col == this.col)
      if (!this.infected)
        if (persons.exists(p => p.id != this.id && p.infected == true && p.row == this.row && p.col == this.col) && randomBelow(10) < 4)
          setInfected

    }
  }
}
