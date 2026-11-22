package com.pvpclient.module.bot;

/** Передвижение «к цели»: общий интерфейс для модуля, лутера и т.д. */
interface Mover {
    enum Go { ARRIVED, MOVING, FAILED }

    Go go(PathFinder.Goal goal, boolean dig);
}
